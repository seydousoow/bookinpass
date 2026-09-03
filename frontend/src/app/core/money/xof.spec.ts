import { formatXof } from './xof';

describe('formatXof', () => {
  it('n’affiche aucune décimale', () => {
    expect(formatXof(185000)).not.toContain(',');
    expect(formatXof(185000)).not.toContain('.');
  });

  it('arrondit à l’unité', () => {
    expect(formatXof(1499.6)).toBe(formatXof(1500));
  });

  it('formate zéro sans erreur', () => {
    expect(formatXof(0)).toContain('0');
  });

  it('mentionne la devise', () => {
    expect(formatXof(1000)).toMatch(/CFA|XOF/);
  });
});
