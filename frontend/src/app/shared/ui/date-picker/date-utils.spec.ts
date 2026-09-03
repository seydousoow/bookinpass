import { addMonths, fromIso, isBetween, monthGrid, toIso, weekdayLabels } from './date-utils';

describe('date-utils', () => {
  it('convertit sans décalage de fuseau', () => {
    expect(toIso(fromIso('2026-09-02'))).toBe('2026-09-02');
    expect(toIso(fromIso('2026-01-01'))).toBe('2026-01-01');
    expect(toIso(fromIso('2026-12-31'))).toBe('2026-12-31');
  });

  it('construit une grille de 42 cellules commençant un lundi', () => {
    const cells = monthGrid(new Date(2026, 8, 1));
    expect(cells).toHaveLength(42);
    expect(fromIso(cells[0].iso).getDay()).toBe(1);
  });

  it('marque correctement les jours hors mois', () => {
    const cells = monthGrid(new Date(2026, 8, 1));
    const inMonth = cells.filter((c) => c.inMonth);
    expect(inMonth).toHaveLength(30);
    expect(inMonth[0].iso).toBe('2026-09-01');
    expect(inMonth.at(-1)!.iso).toBe('2026-09-30');
  });

  it('passe correctement d’une année à l’autre', () => {
    expect(toIso(addMonths(new Date(2026, 11, 15), 1))).toBe('2027-01-01');
    expect(toIso(addMonths(new Date(2026, 0, 15), -1))).toBe('2025-12-01');
  });

  it('exclut les bornes de la plage', () => {
    expect(isBetween('2026-09-05', '2026-09-01', '2026-09-10')).toBe(true);
    expect(isBetween('2026-09-01', '2026-09-01', '2026-09-10')).toBe(false);
    expect(isBetween('2026-09-10', '2026-09-01', '2026-09-10')).toBe(false);
    expect(isBetween('2026-09-05', '2026-09-01', null)).toBe(false);
  });

  it('rend sept libellés de jours à partir du lundi', () => {
    const labels = weekdayLabels('fr-FR');
    expect(labels).toHaveLength(7);
    expect(labels[0].toLowerCase()).toContain('lun');
  });
});
