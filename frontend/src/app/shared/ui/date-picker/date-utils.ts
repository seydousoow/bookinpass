/**
 * Utilitaires de date volontairement sans dépendance externe et sans fuseau :
 * une date de voyage est un jour civil, pas un instant. Le format d'échange est
 * `YYYY-MM-DD', directement plaçable dans l'URL (plan §5.2).
 */
declare const isoDateBrand: unique symbol;

export type IsoDate = string & {
  readonly [isoDateBrand]: true;
};

export function toIso(date: Date): IsoDate {
  const y = date.getFullYear();
  const m = `${date.getMonth() + 1}`.padStart(2, '0');
  const d = `${date.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${d}` as IsoDate;
}

export const fromIso = (iso: IsoDate): Date => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

export const todayIso = (): IsoDate => toIso(new Date());

export const addMonths = (date: Date, count: number): Date => new Date(date.getFullYear(), date.getMonth() + count, 1);

export const isSameDay = (a: IsoDate | null, b: IsoDate | null): boolean => {
  if (!a || !b) {
    return false;
  }
  return a === b;
};

export const isBeforeDay = (a: IsoDate | null, b: IsoDate | null): boolean => {
  if (!a || !b) {
    return false;
  }
  return a < b;
};

export const isAfterDay = (a: IsoDate | null, b: IsoDate | null): boolean => {
  if (!a || !b) {
    return false;
  }
  return a > b;
};

export const isBetween = (day: IsoDate, start: IsoDate | null, end: IsoDate | null): boolean => {
  return !!start && !!end && day > start && day < end;
};

export interface DayCell {
  readonly iso: IsoDate;
  readonly dayNumber: number;
  readonly inMonth: boolean;
}

/** Grille de 6 semaines commençant le lundi, mois débordants inclus. */
export function monthGrid(anchor: Date): DayCell[] {
  const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const offset = (first.getDay() + 6) % 7; // lundi = 0
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);

  const cells: DayCell[] = [];
  for (let i = 0; i < 42; i++) {
    const day = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    cells.push({
      iso: toIso(day),
      dayNumber: day.getDate(),
      inMonth: day.getMonth() === anchor.getMonth(),
    });
  }
  return cells;
}

export const monthLabel = (anchor: Date, locale: string): string =>
  new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(anchor);

export function formatDisplay(iso: IsoDate | null, locale: string): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short', year: 'numeric' }).format(fromIso(iso));
}
