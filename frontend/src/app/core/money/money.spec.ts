import { formatMoney, formatXof } from './money';

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

describe('formatMoney', () => {
  it('applique les décimales propres à chaque devise', () => {
    // Le XOF n'a pas de sous-unité ; l'euro et le dollar en ont une.
    expect(formatMoney(1500, 'XOF')).not.toMatch(/[.,]\d\d(\D|$)/);
    expect(formatMoney(12.5, 'EUR')).toMatch(/12[.,]50/);
    expect(formatMoney(12.5, 'USD')).toMatch(/12[.,]50/);
  });

  it('arrondit les devises sans sous-unité', () => {
    expect(formatMoney(1499.6, 'XOF')).toBe(formatMoney(1500, 'XOF'));
  });

  it('suit la locale demandée', () => {
    expect(formatMoney(1234.5, 'EUR', 'en-US')).toContain('1,234.50');
  });
});
