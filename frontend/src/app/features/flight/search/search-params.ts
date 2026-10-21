import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CABIN_CLASSES, IFlightSearch, TCabinClass } from '@shared/models/search.model';
// Par leur chemin : ce garde est chargé au démarrage (routes), le barrel `@shared/ui` y ajouterait tout le kit.
import { fromIso, IsoDate, todayIso, toIso } from '@shared/ui/date-picker/date-utils';
import { isValidPassengerCount } from '@shared/ui/people-selector/passenger.helper';

/** Paramètres de l'URL de recherche, tels que le routeur les donne (chaînes, ou absents). */
export interface ISearchParams {
  readonly t?: string | null;
  readonly o?: string | null;
  readonly d?: string | null;
  readonly from?: string | null;
  readonly to?: string | null;
  readonly adt?: string | number | null;
  readonly chd?: string | number | null;
  readonly inf?: string | number | null;
  readonly cabin?: string | null;
}

const IATA = /^[A-Z]{3}$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Jour civil réel au format `YYYY-MM-DD` (rejette le 2026-02-30). */
function isIsoDate(value: string | null | undefined): value is IsoDate {
  return !!value && ISO_DATE.test(value) && toIso(fromIso(value as IsoDate)) === value;
}

/** Entier positif ou nul ; un paramètre absent prend la valeur par défaut. */
function count(value: string | number | null | undefined, fallback: number): number {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  const n = Number(value);
  return Number.isInteger(n) && n >= 0 ? n : NaN;
}

/**
 * Recherche décrite par l'URL, ou `null` si elle est incomplète ou incohérente.
 * Mêmes règles que le backend (`FlightSearchRequest`) : une URL acceptée ici ne lui vaut pas un 400.
 */
export function parseSearchParams(params: ISearchParams): IFlightSearch | null {
  const { t, o, d, from, to } = params;
  if ((t !== 'oneway' && t !== 'round') || !o || !d || !IATA.test(o) || !IATA.test(d) || o === d) {
    return null;
  }
  if (!isIsoDate(from) || from < todayIso()) {
    return null;
  }
  if (t === 'round' && (!isIsoDate(to) || to < from)) {
    return null;
  }

  const passengers = { adults: count(params.adt, 1), children: count(params.chd, 0), infants: count(params.inf, 0) };
  if (!isValidPassengerCount(passengers)) {
    return null;
  }

  const cabin = params.cabin || 'economy';
  if (!CABIN_CLASSES.includes(cabin as TCabinClass)) {
    return null;
  }

  return {
    tripType: t,
    origin: o,
    destination: d,
    dates: { start: from, end: t === 'round' ? (to as IsoDate) : null },
    passengers,
    cabin: [cabin as TCabinClass],
  };
}

/** Une URL de recherche invalide renvoie directement à l'accueil, sans appel à l'API. */
export const flightSearchParamsGuard: CanActivateFn = (route) =>
  parseSearchParams(route.queryParams) ? true : inject(Router).createUrlTree(['/flight']);
