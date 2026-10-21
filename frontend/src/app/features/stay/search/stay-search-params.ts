import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { IStayGuests, IStaySearch } from '@shared/models/stay.model';
// Par leur chemin : chargé au démarrage (routes), le barrel `@shared/ui` y ajouterait tout le kit.
import { fromIso, IsoDate, todayIso, toIso } from '@shared/ui/date-picker/date-utils';

/** Paramètres de l'URL de recherche d'hébergement, tels que le routeur les donne. */
export interface IStaySearchParams {
  readonly l?: string | null;
  readonly in?: string | null;
  readonly out?: string | null;
  readonly adt?: string | number | null;
  /** Âges des enfants séparés par des virgules : `7,4`. */
  readonly chd?: string | null;
  readonly rooms?: string | number | null;
}

/** Limites Duffel Stays, reprises par le backend (`StaySearchRequest`). */
export const STAY_LIMITS = {
  maxRooms: 8,
  maxAdults: 16,
  maxChildren: 8,
  maxChildAge: 17,
  maxNights: 99,
  maxDaysAhead: 330,
} as const;

/** Identifiant GeoNames de la ville (`/stays/destinations`). */
const CITY_ID = /^[1-9]\d{0,8}$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MS = 86_400_000;

function isIsoDate(value: string | null | undefined): value is IsoDate {
  return !!value && ISO_DATE.test(value) && toIso(fromIso(value as IsoDate)) === value;
}

function count(value: string | number | null | undefined, fallback: number): number {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  const n = Number(value);
  return Number.isInteger(n) ? n : NaN;
}

/** Nuits entre deux jours civils. */
export function nightsBetween(checkIn: string, checkOut: string): number {
  return Math.round((fromIso(checkOut as IsoDate).getTime() - fromIso(checkIn as IsoDate).getTime()) / DAY_MS);
}

/** Dernier jour d'arrivée accepté par Duffel. */
export function maxCheckIn(): IsoDate {
  const date = fromIso(todayIso());
  date.setDate(date.getDate() + STAY_LIMITS.maxDaysAhead);
  return toIso(date);
}

export function isValidStayGuests({ rooms, adults, childAges }: IStayGuests): boolean {
  return (
    Number.isInteger(rooms) &&
    rooms >= 1 &&
    rooms <= STAY_LIMITS.maxRooms &&
    Number.isInteger(adults) &&
    adults >= rooms &&
    adults <= STAY_LIMITS.maxAdults &&
    childAges.length <= STAY_LIMITS.maxChildren &&
    childAges.every((age) => Number.isInteger(age) && age >= 0 && age <= STAY_LIMITS.maxChildAge)
  );
}

/** Arrivée aujourd'hui ou plus tard (330 jours au plus), départ après l'arrivée, 99 nuits au plus. */
export function isValidStayDates(checkIn: string | null, checkOut: string | null): boolean {
  if (!isIsoDate(checkIn) || !isIsoDate(checkOut) || checkIn < todayIso() || checkIn > maxCheckIn()) {
    return false;
  }
  const nights = nightsBetween(checkIn, checkOut);
  return nights >= 1 && nights <= STAY_LIMITS.maxNights;
}

/**
 * Recherche décrite par l'URL, ou `null` si elle est incomplète ou incohérente.
 * Mêmes règles que le backend : une URL acceptée ici ne lui vaut pas un 400.
 */
export function parseStaySearchParams(params: IStaySearchParams): IStaySearch | null {
  const { l } = params;
  if (!l || !CITY_ID.test(l) || !isValidStayDates(params.in ?? null, params.out ?? null)) {
    return null;
  }
  const childAges = params.chd ? params.chd.split(',').map((age) => (age.trim() === '' ? NaN : Number(age))) : [];
  const guests: IStayGuests = { rooms: count(params.rooms, 1), adults: count(params.adt, 2), childAges };
  if (!isValidStayGuests(guests)) {
    return null;
  }
  return { location: l, dates: { start: params.in as IsoDate, end: params.out as IsoDate }, guests };
}

/** Paramètres d'URL d'une recherche : les valeurs par défaut sont omises. */
export function toStayQueryParams({ location, dates, guests }: IStaySearch): Record<string, string | number> {
  const params: Record<string, string | number> = { l: location, in: String(dates.start), out: String(dates.end), adt: guests.adults };
  if (guests.childAges.length) {
    params['chd'] = guests.childAges.join(',');
  }
  if (guests.rooms > 1) {
    params['rooms'] = guests.rooms;
  }
  return params;
}

/** Une URL de recherche invalide renvoie à l'accueil des hébergements, sans appel à l'API. */
export const staySearchParamsGuard: CanActivateFn = (route) =>
  parseStaySearchParams(route.queryParams) ? true : inject(Router).createUrlTree(['/stays']);
