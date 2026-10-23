/** Lieu desservi par car : ville (identifiant GeoNames) ou aéroport (code IATA). */
export interface IBusPlace {
  readonly id: string;
  readonly name: string;
  readonly country_code: string;
  /** Lieux atteignables au départ de celui-ci. */
  readonly destinations: readonly string[];
}

export interface IBusStation {
  /** Ville (ou aéroport) de la gare. */
  readonly place: string;
  readonly name: string;
  readonly address: string | null;
  /** E.164 ; celui de l'exploitant si la gare n'en a pas. */
  readonly phone: string | null;
}

/** Heures en UTC (`2026-10-25T07:00:00Z`) : l'heure de Dakar, à GMT toute l'année. À afficher avec `timeZone: 'UTC'`. */
export interface IBusDeparture {
  readonly route_code: string;
  readonly network: 'interurban' | 'international' | 'airport';
  readonly operator: string;
  readonly origin: IBusStation;
  readonly destination: IBusStation;
  readonly departs_at: string;
  /** Service continu : dernier départ, le billet vaut toute la plage ; `null` pour un départ à heure fixe. */
  readonly last_departure_at: string | null;
  /** Par passager ; `total` pour tous les passagers demandés. Décimal, en `currency` (XOF). */
  readonly price: string;
  readonly total: string;
  readonly currency: string;
  /** `null` : places non comptées (service continu). */
  readonly seats_left: number | null;
  readonly available: boolean;
}

export interface IBusSearch {
  readonly from: string;
  readonly to: string;
  /** Jour du voyage à Dakar, `YYYY-MM-DD`. */
  readonly date: string;
  readonly passengers: number;
}

export interface IBusPassenger {
  given_name: string;
  family_name: string;
}

export interface IBusTicket {
  readonly given_name: string;
  readonly family_name: string;
  /** Présent une fois la réservation confirmée. */
  readonly code: string | null;
}

export interface IBusBooking {
  readonly reference: string;
  readonly status: 'held' | 'confirmed' | 'released';
  readonly operator: string;
  readonly operator_phone: string | null;
  readonly origin: IBusStation;
  readonly destination: IBusStation;
  readonly departs_at: string;
  readonly last_departure_at: string | null;
  readonly tickets: readonly IBusTicket[];
  readonly total: string;
  readonly currency: string;
}
