import { IFlightOffer, IOfferSlice } from '@shared/models/flight.model';
import { sellingTotal } from '@core/money/pricing';
import { sliceStops, TPriceInDisplayCurrency } from '../filter/flight-filter.util';

/** Tris proposés au-dessus des résultats ; « le meilleur choix » par défaut. */
export const FLIGHT_SORTS = ['cheapest', 'best', 'fastest'] as const;
export type TFlightSort = (typeof FLIGHT_SORTS)[number];

/**
 * Poids du « meilleur choix » : tarif, temps de trajet, escales et bagages, chacun ramené entre 0 (le mieux parmi
 * les offres affichées) et 1 (le moins bien). Le prix pèse le plus : c'est le premier critère de choix.
 */
export interface IFlightWeightAlgorithm {
  price: number;
  duration: number;
  stops: number;
  baggage: number;
}
export const BEST_WEIGHTS: IFlightWeightAlgorithm = { price: 0.45, duration: 0.3, stops: 0.15, baggage: 0.1 } as const;

/** Durée ISO 8601 de Duffel (`PT8H20M`, `P1DT2H5M`) en minutes ; 0 si illisible. */
export function durationMinutes(duration: string | null | undefined): number {
  const match = duration?.match(/^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/);
  if (!match) {
    return 0;
  }
  return Number(match[1] ?? 0) * 1440 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0);
}

/** Temps de trajet total de l'offre : aller + retour, escales comprises. */
export function offerMinutes(offer: IFlightOffer): number {
  return offer.slices.reduce((total, slice) => total + durationMinutes(slice.duration), 0);
}

function offerStops(offer: IFlightOffer): number {
  return offer.slices.reduce((total, slice) => total + sliceStops(slice), 0);
}

/** Bagage en soute inclus pour chaque passager, sur chaque vol. */
function includesCheckedBag(slice: IOfferSlice): boolean {
  return slice.segments.every((segment) =>
    segment.passengers.every((passenger) => passenger.baggages.some((baggage) => baggage.type === 'checked' && baggage.quantity > 0)),
  );
}

interface IRanked {
  readonly offer: IFlightOffer;
  readonly price: number;
  readonly minutes: number;
  readonly stops: number;
  readonly checkedBag: boolean;
}

/** Valeur ramenée entre 0 et 1 sur l'étendue observée ; toutes égales : 0 (aucune n'est pénalisée). */
function scale(value: number, min: number, max: number): number {
  return max > min ? (value - min) / (max - min) : 0;
}

function bestScores(ranked: readonly IRanked[]): Map<IFlightOffer, number> {
  const bounds = (pick: (r: IRanked) => number) => {
    const values = ranked.map(pick);
    return [Math.min(...values), Math.max(...values)] as const;
  };
  const [minPrice, maxPrice] = bounds((r) => r.price);
  const [minMinutes, maxMinutes] = bounds((r) => r.minutes);
  const [minStops, maxStops] = bounds((r) => r.stops);
  return new Map(
    ranked.map((r) => [
      r.offer,
      BEST_WEIGHTS.price * scale(r.price, minPrice, maxPrice) +
        BEST_WEIGHTS.duration * scale(r.minutes, minMinutes, maxMinutes) +
        BEST_WEIGHTS.stops * scale(r.stops, minStops, maxStops) +
        BEST_WEIGHTS.baggage * (r.checkedBag ? 0 : 1),
    ]),
  );
}

/**
 * Offres triées (nouvelle liste, l'entrée n'est pas modifiée). Prix comparés dans la devise d'affichage (frais BIP
 * compris), en devise d'origine si le taux manque. À égalité, le moins cher, puis le plus court, passe devant.
 */
export function sortFlights(offers: readonly IFlightOffer[], sort: TFlightSort, toDisplay: TPriceInDisplayCurrency = (amount) => amount): IFlightOffer[] {
  const ranked: IRanked[] = offers.map((offer) => {
    const amount = sellingTotal(offer);
    return {
      offer,
      price: toDisplay(amount, offer.total_currency) ?? amount,
      minutes: offerMinutes(offer),
      stops: offerStops(offer),
      checkedBag: offer.slices.every(includesCheckedBag),
    };
  });
  const scores = sort === 'best' ? bestScores(ranked) : null;
  const key = (r: IRanked): number => (sort === 'cheapest' ? r.price : sort === 'fastest' ? r.minutes : scores!.get(r.offer)!);
  return [...ranked]
    .sort((a, b) => key(a) - key(b) || a.price - b.price || a.minutes - b.minutes)
    .map((r) => r.offer);
}
