import { IStayResult, TStayAmenity } from '@shared/models';
import { staySellingTotal } from '@core/money/pricing';

export type TStaySort = 'price' | 'rating' | 'reviews';
export const STAY_SORTS: readonly TStaySort[] = ['price', 'rating', 'reviews'];

/** Notes minimales proposées pour les avis clients (sur 10). */
export const REVIEW_THRESHOLDS = [9, 8, 7] as const;

export interface IStayFilter {
  /** Prix total maximal, dans la devise d'affichage choisie par l'utilisateur ; `null` : pas de plafond. */
  readonly maxPrice: number | null;
  /** Étoiles acceptées ; vide : toutes, y compris les établissements non classés. */
  readonly stars: readonly number[];
  readonly minReviewScore: number | null;
  /** Équipements exigés, tous à la fois. */
  readonly amenities: readonly TStayAmenity[];
}

export const NO_STAY_FILTER: IStayFilter = { maxPrice: null, stars: [], minReviewScore: null, amenities: [] };

export function isFiltering(filter: IStayFilter): boolean {
  return filter.maxPrice !== null || filter.stars.length > 0 || filter.minReviewScore !== null || filter.amenities.length > 0;
}

/** Montant d'un résultat dans la devise d'affichage ; `null` si le taux manque. */
export type TPriceInDisplayCurrency = (amount: number, currency: string) => number | null;

export function applyStayFilter(
  results: readonly IStayResult[],
  filter: IStayFilter,
  toDisplay: TPriceInDisplayCurrency = (amount) => amount,
): IStayResult[] {
  return results.filter(({ accommodation, cheapest_rate_total_amount, cheapest_rate_currency }) => {
    const price = toDisplay(staySellingTotal(cheapest_rate_total_amount, cheapest_rate_currency), cheapest_rate_currency);
    if (filter.maxPrice !== null && price !== null && price > filter.maxPrice) {
      return false;
    }
    if (filter.stars.length && !filter.stars.includes(accommodation.rating ?? 0)) {
      return false;
    }
    if (filter.minReviewScore !== null && (accommodation.review_score ?? 0) < filter.minReviewScore) {
      return false;
    }
    const amenities = new Set(accommodation.amenities?.map((a) => a.type) ?? []);
    return filter.amenities.every((a) => amenities.has(a));
  });
}

/** Tri stable : à critère égal, le moins cher d'abord. */
export function sortStays(results: readonly IStayResult[], sort: TStaySort): IStayResult[] {
  const price = (r: IStayResult): number => Number(r.cheapest_rate_total_amount);
  const key: Record<TStaySort, (r: IStayResult) => number> = {
    price: () => 0,
    rating: (r) => -(r.accommodation.rating ?? 0),
    reviews: (r) => -(r.accommodation.review_score ?? 0),
  };
  return [...results].sort((a, b) => key[sort](a) - key[sort](b) || price(a) - price(b));
}
