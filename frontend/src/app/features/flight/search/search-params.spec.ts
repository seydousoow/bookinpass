import { toIso } from '@shared/ui';
import { parseSearchParams } from './search-params';

const inDays = (days: number): string => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return toIso(date);
};

describe('parseSearchParams', () => {
  const oneWay = { t: 'oneway', o: 'DSS', d: 'CDG', from: inDays(10) };

  it('accepte un aller simple complet, avec les valeurs par défaut', () => {
    expect(parseSearchParams(oneWay)).toEqual({
      tripType: 'oneway',
      origin: 'DSS',
      destination: 'CDG',
      dates: { start: oneWay.from, end: null },
      passengers: { adults: 1, children: 0, infants: 0 },
      cabin: ['economy'],
    });
  });

  it('lit les passagers et la cabine de l’URL (chaînes)', () => {
    const search = parseSearchParams({ ...oneWay, adt: '2', chd: '1', inf: '1', cabin: 'business' });
    expect(search?.passengers).toEqual({ adults: 2, children: 1, infants: 1 });
    expect(search?.cabin).toEqual(['business']);
  });

  it('accepte un aller-retour dont le retour suit le départ', () => {
    expect(parseSearchParams({ ...oneWay, t: 'round', to: inDays(20) })?.dates.end).toBe(inDays(20));
  });

  it.each([
    ['type inconnu', { t: 'multi' }],
    ['origine absente', { o: undefined }],
    ['code IATA mal formé', { d: 'cdg' }],
    ['même aéroport', { d: 'DSS' }],
    ['date passée', { from: inDays(-1) }],
    ['date impossible', { from: '2026-02-30' }],
    ['retour manquant', { t: 'round' }],
    ['retour avant le départ', { t: 'round', to: inDays(5) }],
    ['aucun adulte', { adt: '0' }],
    ['plus de bébés que d’adultes', { inf: '2' }],
    ['nombre non entier', { adt: '1.5' }],
    ['plus de 9 passagers', { adt: '5', chd: '5' }],
    ['cabine inconnue', { cabin: 'luxury' }],
  ])('refuse : %s', (_, override) => {
    expect(parseSearchParams({ ...oneWay, ...override })).toBeNull();
  });
});
