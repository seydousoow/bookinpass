import { IBookedOrder, IBookedPassenger, IBookedSlice } from './manage-booking.service';

export type TTripKind = 'one-way' | 'return' | 'multi';

/** Aller simple, aller-retour (le retour revient au point de départ) ou multi-destinations. */
export function tripKind(order: IBookedOrder): TTripKind {
  const slices = order.slices;
  if (slices.length === 1) {
    return 'one-way';
  }
  const [first, second] = slices;
  return slices.length === 2 && first.origin.iata_code === second.destination.iata_code && first.destination.iata_code === second.origin.iata_code
    ? 'return'
    : 'multi';
}

export function cityOf(place: { readonly iata_code: string; readonly city_name?: string | null; readonly name?: string | null }): string {
  return place.city_name || place.name || place.iata_code;
}

export function initials(passenger: IBookedPassenger): string {
  return `${passenger.given_name.trim().charAt(0)}${passenger.family_name.trim().charAt(0)}`.toUpperCase();
}

/** Numéro de billet électronique du passager, s'il est émis. */
export function ticketNumber(order: IBookedOrder, passengerId: string): string | null {
  return order.documents?.find((d) => d.type === 'electronic_ticket' && d.passenger_ids.includes(passengerId))?.unique_identifier ?? null;
}

/** Escales d'un trajet : correspondances (arrivée d'un segment non final) puis arrêts techniques. */
export function stopsOf(slice: IBookedSlice): string[] {
  return slice.segments.flatMap((segment, index) => [
    ...(segment.stops ?? []).map((stop) => stop.airport.iata_code),
    ...(index < slice.segments.length - 1 ? [segment.destination.iata_code] : []),
  ]);
}

/** Compagnies d'un trajet, une par segment (logo affiché sur chaque tronçon de la frise). */
export function carriersOf(slice: IBookedSlice): { name: string; logo: string | null }[] {
  return slice.segments.map((s) => ({
    name: s.marketing_carrier.name,
    logo: s.marketing_carrier.logo_symbol_url ?? s.operating_carrier?.logo_symbol_url ?? null,
  }));
}

/**
 * Temps restant avant le premier départ, en jours et heures. Les heures Duffel sont locales à l'aéroport, sans
 * fuseau : lues comme heure locale du navigateur, l'écart reste une approximation (à quelques heures près).
 */
export function untilDeparture(order: IBookedOrder, now = Date.now()): { days: number; hours: number } | null {
  const departure = order.slices[0]?.segments[0]?.departing_at;
  const ms = departure ? new Date(departure).getTime() - now : 0;
  if (!(ms > 0)) {
    return null;
  }
  const hours = Math.floor(ms / 3_600_000);
  return { days: Math.floor(hours / 24), hours: hours % 24 };
}
