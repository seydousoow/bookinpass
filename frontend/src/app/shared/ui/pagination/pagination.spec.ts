import { pageItems } from './pagination';

describe('pageItems', () => {
  it('liste toutes les pages quand il y en a peu', () => {
    expect(pageItems(1, 4)).toEqual([1, 2, 3, 4]);
  });

  it('abrège loin de la page courante', () => {
    expect(pageItems(6, 12)).toEqual([1, 'gap', 5, 6, 7, 'gap', 12]);
  });

  it('affiche un seul numéro manquant plutôt qu’un « … »', () => {
    expect(pageItems(4, 12)).toEqual([1, 2, 3, 4, 5, 'gap', 12]);
  });
});
