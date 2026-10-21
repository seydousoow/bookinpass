import { toIso } from '@shared/ui/date-picker/date-utils';
import { isValidStayGuests, nightsBetween, parseStaySearchParams, toStayQueryParams } from './stay-search-params';

const inDays = (days: number): string => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return toIso(date);
};

describe('parseStaySearchParams', () => {
  const stay = { l: '2253354', in: inDays(10), out: inDays(13) };

  it('accepte une recherche complète, avec les valeurs par défaut', () => {
    expect(parseStaySearchParams(stay)).toEqual({
      location: '2253354',
      dates: { start: stay.in, end: stay.out },
      guests: { rooms: 1, adults: 2, childAges: [] },
    });
  });

  it('lit les âges des enfants et le nombre de chambres', () => {
    expect(parseStaySearchParams({ ...stay, adt: '3', chd: '7,0,17', rooms: '2' })?.guests).toEqual({ rooms: 2, adults: 3, childAges: [7, 0, 17] });
  });

  it.each([
    ['lieu absent', { l: undefined }],
    ['lieu mal formé', { l: 'dkr' }],
    ['arrivée passée', { in: inDays(-1) }],
    ['date impossible', { in: '2026-02-30' }],
    ['départ le jour de l’arrivée', { out: inDays(10) }],
    ['départ avant l’arrivée', { out: inDays(9) }],
    ['plus de 99 nuits', { out: inDays(110) }],
    ['arrivée au-delà de 330 jours', { in: inDays(331), out: inDays(333) }],
    ['moins d’un adulte par chambre', { adt: '1', rooms: '2' }],
    ['aucun adulte', { adt: '0' }],
    ['enfant de 18 ans', { chd: '18' }],
    ['âge vide', { chd: '7,' }],
    ['âge non entier', { chd: '4.5' }],
    ['plus de 8 chambres', { adt: '9', rooms: '9' }],
  ])('refuse : %s', (_, override) => {
    expect(parseStaySearchParams({ ...stay, ...override })).toBeNull();
  });
});

describe('toStayQueryParams', () => {
  it('omet les valeurs par défaut et reste relisible', () => {
    const search = parseStaySearchParams({ l: '2253354', in: inDays(10), out: inDays(12), adt: '2' })!;
    expect(toStayQueryParams(search)).toEqual({ l: '2253354', in: inDays(10), out: inDays(12), adt: 2 });
    const withChildren = { ...search, guests: { rooms: 2, adults: 2, childAges: [5, 9] } };
    expect(parseStaySearchParams(toStayQueryParams(withChildren) as never)).toEqual(withChildren);
  });
});

describe('règles', () => {
  it('compte les nuits entre deux jours', () => {
    expect(nightsBetween('2026-10-30', '2026-11-02')).toBe(3);
  });

  it('exige un adulte par chambre', () => {
    expect(isValidStayGuests({ rooms: 2, adults: 2, childAges: [] })).toBe(true);
    expect(isValidStayGuests({ rooms: 3, adults: 2, childAges: [] })).toBe(false);
  });
});
