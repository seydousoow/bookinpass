import { fractionDigits } from '@shared/models/money.model';

/**
 * Prix de vente Book In Pass, calculés à l'affichage à partir des prix Duffel (relus et reconfirmés à chaque étape) :
 * - billet : frais de service BIP, 3 % pour un voyage dans un même pays, 5 % sinon ;
 * - services annexes (bagages, sièges) : +20 % sur chaque prix.
 */
export const SERVICE_FEE_RATES = { sameCountry: 0.03, other: 0.05 } as const;
export const ANCILLARY_MARKUP = 0.2;
/** Hébergement : frais de service BIP sur le prix Duffel du séjour (mêmes règles côté serveur, `StayPricing`). */
export const STAY_SERVICE_FEE_RATE = 0.05;

interface IPlace {
  readonly iata_country_code?: string | null;
}

/** Ce qu'il faut d'une offre Duffel (recherche, détail ou offre tarifée) pour en calculer les frais. */
export interface IPricedOffer {
  readonly total_amount: string;
  readonly total_currency: string;
  readonly slices: readonly { readonly origin: IPlace; readonly destination: IPlace }[];
  readonly available_services?: readonly { readonly id: string; readonly total_amount: string }[] | null;
  /** Présents sur une offre tarifée : son `total_amount` comprend alors ces services au prix Duffel. */
  readonly intended_services?: readonly { readonly id: string; readonly quantity: number }[] | null;
}

/** Arrondi au plus loin de zéro, aux décimales de la devise. */
export function roundMoney(amount: number, currency: string): number {
  const factor = 10 ** fractionDigits(currency);
  const scaled = Math.abs(amount) * factor;
  // `Number.EPSILON` : 12.5 × 1.2 vaut 15.000000000000002, 1.005 × 100 vaut 100.49999…
  return (Math.sign(amount) * Math.round(scaled + Number.EPSILON * scaled)) / factor;
}

/** Taux des frais : 3 % si tous les départs et arrivées sont dans un même pays, 5 % sinon (pays inconnu compris). */
export function serviceFeeRate(offer: IPricedOffer): number {
  const places = offer.slices.flatMap((slice) => [slice.origin, slice.destination]);
  const countries = new Set(places.map((p) => p.iata_country_code || null));
  return places.length && !countries.has(null) && countries.size === 1 ? SERVICE_FEE_RATES.sameCountry : SERVICE_FEE_RATES.other;
}

/**
 * Prix Duffel du billet seul : le total, moins les services prévus d'une offre tarifée (comptés à part, majorés
 * comme services annexes). Les suppléments éventuels de la tarification restent dans le billet.
 */
export function ticketAmount(offer: IPricedOffer): number {
  const prices = new Map((offer.available_services ?? []).map((s) => [s.id, Number(s.total_amount)]));
  const services = (offer.intended_services ?? []).reduce((sum, s) => sum + (prices.get(s.id) ?? 0) * s.quantity, 0);
  return roundMoney(Number(offer.total_amount) - services, offer.total_currency);
}

/** Frais de service BIP d'un billet, dans `total_currency`. */
export function serviceFee(offer: IPricedOffer): number {
  return roundMoney(ticketAmount(offer) * serviceFeeRate(offer), offer.total_currency);
}

/** Prix de vente du billet seul : prix Duffel du billet + frais de service BIP (services annexes à part). */
export function sellingTotal(offer: IPricedOffer): number {
  return roundMoney(ticketAmount(offer) + serviceFee(offer), offer.total_currency);
}

/** Prix de vente d'un service annexe : prix Duffel + 20 %. */
export function ancillaryPrice(amount: string | number, currency: string): number {
  return roundMoney(Number(amount) * (1 + ANCILLARY_MARKUP), currency);
}

/** Frais de service BIP d'un séjour : 5 % du prix Duffel, dans sa devise. */
export function stayServiceFee(amount: string | number, currency: string): number {
  return roundMoney(Number(amount) * STAY_SERVICE_FEE_RATE, currency);
}

/** Prix de vente d'un séjour : prix Duffel + frais de service BIP (le montant réglé sur place reste à part). */
export function staySellingTotal(amount: string | number, currency: string): number {
  return roundMoney(Number(amount) + stayServiceFee(amount, currency), currency);
}
