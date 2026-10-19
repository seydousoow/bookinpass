import { IFlightOffer, IOfferSlice } from '@shared/models/flight.model';
import { sellingTotal } from '@core/money/pricing';

/** Limites d'escales proposées : toujours une de choisie, la plus large par défaut. */
export const STOP_OPTIONS = [0, 1, 2] as const;

export interface IFlightFilter {
  /** Escales admises par trajet (aller, retour). Hors réinitialisation : c'est un choix, pas un filtre à lever. */
  readonly maxStops: number;
  /** Bagage à main inclus sur chaque trajet. */
  readonly cabinBag: boolean;
  /** Bagage en soute inclus sur chaque trajet. */
  readonly holdBag: boolean;
  /** Fourchette de prix dans la devise d'affichage ; `null` : pas de limite. */
  readonly price: { readonly min: number; readonly max: number } | null;
  /** Compagnies retenues (code IATA) ; vide : toutes. */
  readonly airlines: readonly string[];
}

export const NO_FLIGHT_FILTER: IFlightFilter = { maxStops: STOP_OPTIONS[STOP_OPTIONS.length - 1], cabinBag: false, holdBag: false, price: null, airlines: [] };

/** Réinitialisation : tout est levé sauf la limite d'escales choisie. */
export function resetFlightFilter(filter: IFlightFilter): IFlightFilter {
  return { ...NO_FLIGHT_FILTER, maxStops: filter.maxStops };
}

/** Montant d'une offre dans la devise d'affichage ; `null` si le taux manque. */
export type TPriceInDisplayCurrency = (amount: number, currency: string) => number | null;

export function isFlightFiltering(filter: IFlightFilter): boolean {
  return filter.cabinBag || filter.holdBag || filter.price !== null || filter.airlines.length > 0;
}

/** Escales d'un trajet : correspondances et arrêts techniques. */
export function sliceStops(slice: IOfferSlice): number {
  return slice.segments.length - 1 + slice.segments.reduce((total, segment) => total + (segment.stops?.length ?? 0), 0);
}

function hasBaggage(slice: IOfferSlice, type: 'carry_on' | 'checked'): boolean {
  return slice.segments.every((segment) => segment.passengers.some((passenger) => passenger.baggages.some((baggage) => baggage.type === type && baggage.quantity > 0)));
}

export function applyFlightFilter(
  offers: readonly IFlightOffer[],
  filter: IFlightFilter,
  toDisplay: TPriceInDisplayCurrency = (amount) => amount,
): IFlightOffer[] {
  return offers.filter((offer) => {
    const { maxStops, cabinBag, holdBag, price, airlines } = filter;
    if (offer.slices.some((slice) => sliceStops(slice) > maxStops)) {
      return false;
    }
    if (cabinBag && !offer.slices.every((slice) => hasBaggage(slice, 'carry_on'))) {
      return false;
    }
    if (holdBag && !offer.slices.every((slice) => hasBaggage(slice, 'checked'))) {
      return false;
    }
    if (airlines.length && !airlines.includes(offer.owner.iata_code ?? '')) {
      return false;
    }
    if (price) {
      const amount = toDisplay(sellingTotal(offer), offer.total_currency);
      if (amount !== null && (amount < price.min || amount > price.max)) {
        return false;
      }
    }
    return true;
  });
}

/**
 * Coche ou décoche une compagnie. Liste vide = toutes cochées : décocher la première en retient toutes les autres,
 * et tout recocher revient à la liste vide.
 */
export function toggleAirline(selected: readonly string[], all: readonly string[], code: string, checked: boolean): string[] {
  const current = selected.length ? selected : all;
  const next = checked ? [...new Set([...current, code])] : current.filter((c) => c !== code);
  return all.every((c) => next.includes(c)) ? [] : next;
}
