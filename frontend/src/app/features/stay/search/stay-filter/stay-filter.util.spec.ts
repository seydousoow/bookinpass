import { IStayResult } from '@shared/models';
import { applyStayFilter, NO_STAY_FILTER, sortStays } from './stay-filter.util';

const result = (id: string, price: string, rating: number | null, score: number | null, amenities: string[] = []): IStayResult =>
  ({
    id,
    cheapest_rate_total_amount: price,
    cheapest_rate_currency: 'EUR',
    accommodation: { rating, review_score: score, amenities: amenities.map((type) => ({ type, description: type })) },
  }) as unknown as IStayResult;

const results = [
  result('a', '300.00', 5, 9.1, ['wifi', 'pool']),
  result('b', '120.00', 3, 7.9, ['wifi']),
  result('c', '90.00', null, 9.4),
  result('d', '200.00', 4, null, ['pool']),
];
const ids = (list: readonly IStayResult[]): string[] => list.map((r) => r.id);

describe('applyStayFilter', () => {
  it('garde tout sans critère', () => {
    expect(ids(applyStayFilter(results, NO_STAY_FILTER))).toEqual(['a', 'b', 'c', 'd']);
  });

  it('plafonne le prix total', () => {
    expect(ids(applyStayFilter(results, { ...NO_STAY_FILTER, maxPrice: 150 }))).toEqual(['b', 'c']);
  });

  it('compare le budget au prix affiché, frais de service BIP (5 %) compris', () => {
    // 145 € Duffel = 152,25 € affichés : au-dessus d'un budget de 150 €.
    expect(ids(applyStayFilter([result('e', '145.00', 3, 8)], { ...NO_STAY_FILTER, maxPrice: 150 }))).toEqual([]);
    expect(ids(applyStayFilter([result('e', '142.00', 3, 8)], { ...NO_STAY_FILTER, maxPrice: 150 }))).toEqual(['e']);
  });

  it('filtre les étoiles ; un établissement non classé compte zéro étoile', () => {
    expect(ids(applyStayFilter(results, { ...NO_STAY_FILTER, stars: [4, 5] }))).toEqual(['a', 'd']);
  });

  it('exige une note minimale ; sans avis, il est écarté', () => {
    expect(ids(applyStayFilter(results, { ...NO_STAY_FILTER, minReviewScore: 8 }))).toEqual(['a', 'c']);
  });

  it('exige chaque équipement demandé', () => {
    expect(ids(applyStayFilter(results, { ...NO_STAY_FILTER, amenities: ['wifi', 'pool'] }))).toEqual(['a']);
  });
});

describe('sortStays', () => {
  it('trie par prix, étoiles ou avis, le moins cher d’abord à égalité', () => {
    expect(ids(sortStays(results, 'price'))).toEqual(['c', 'b', 'd', 'a']);
    expect(ids(sortStays(results, 'rating'))).toEqual(['a', 'd', 'b', 'c']);
    expect(ids(sortStays(results, 'reviews'))).toEqual(['c', 'a', 'b', 'd']);
  });

  it('ne modifie pas la liste reçue', () => {
    sortStays(results, 'price');
    expect(ids(results)).toEqual(['a', 'b', 'c', 'd']);
  });
});
