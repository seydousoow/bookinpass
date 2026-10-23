import { IBusSearch } from '@shared/models/bus.model';

/** Vente ouverte 60 jours à l'avance (comme le serveur), 9 passagers au plus. */
export const BUS_LIMITS = { daysAhead: 60, maxPassengers: 9 } as const;

const PLACE = /^(\d{1,10}|[A-Z]{3})$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Jour civil à Dakar (GMT toute l'année) : la date UTC, pas celle du fuseau du navigateur. */
export function dakarToday(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/** `YYYY-MM-DD` décalé de `days` jours. */
export function shiftDay(date: string, days: number): string {
  const day = new Date(`${date}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

/** Recherche lue dans l'URL ; `null` si un critère manque ou n'est pas valable. */
export function parseBusSearch(params: Partial<Record<keyof IBusSearch, string | undefined>>): IBusSearch | null {
  const passengers = Number(params.passengers ?? 1);
  if (!params.from || !params.to || !params.date || !PLACE.test(params.from) || !PLACE.test(params.to) || !DATE.test(params.date)) {
    return null;
  }
  if (!Number.isInteger(passengers) || passengers < 1 || passengers > BUS_LIMITS.maxPassengers || params.from === params.to) {
    return null;
  }
  return { from: params.from, to: params.to, date: params.date, passengers };
}

export function toBusQueryParams(search: IBusSearch): Record<string, string | number> {
  return { from: search.from, to: search.to, date: search.date, passengers: search.passengers };
}
