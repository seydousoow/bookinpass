export type StaffRole = 'admin' | 'manager' | 'station_chief' | 'inspector';

export interface Station {
  readonly id: number;
  readonly code: string;
  readonly name: string;
  /** Ville (ou aéroport) de la gare. */
  readonly place: string;
  readonly address: string | null;
  readonly phone: string | null;
}

export interface StaffUser {
  readonly id: number;
  readonly username: string;
  readonly full_name: string;
  readonly phone: string | null;
  readonly role: StaffRole;
  readonly station: Station | null;
  readonly active: boolean;
  readonly must_change_password: boolean;
  readonly last_login_at: string | null;
}

/** Heures en UTC (`2026-10-25T07:00:00Z`) : l'heure de Dakar, à GMT toute l'année. */
export interface Departure {
  readonly id: number;
  readonly route_id: number;
  readonly route_code: string;
  readonly origin: Station;
  readonly destination: Station;
  readonly departs_at: string;
  readonly last_departure_at: string | null;
  /** `null` : service continu, places non comptées. */
  readonly capacity: number | null;
  readonly sold: number;
  readonly held: number;
  readonly passengers: number;
  readonly scanned: number;
  readonly luggage: number;
  readonly open_incidents: number;
  readonly departed_at: string | null;
  readonly arrived_at: string | null;
}

export interface Passenger {
  readonly code: string;
  readonly given_name: string;
  readonly family_name: string;
  readonly reference: string;
  readonly phone: string;
  readonly scanned_at: string | null;
  readonly scanned_by: string | null;
}

export interface Luggage {
  readonly code: string;
  readonly departure_id: number;
  readonly origin: string;
  readonly destination: string;
  readonly departs_at: string;
  readonly sender_name: string;
  readonly sender_phone: string;
  readonly sender_id_number: string | null;
  readonly receiver_name: string;
  readonly receiver_phone: string;
  readonly description: string | null;
  readonly weight_kg: string;
  readonly price: string;
  readonly currency: string;
  readonly status: 'registered' | 'delivered';
  readonly registered_by: string;
  readonly created_at: string;
  readonly delivered_at: string | null;
}

export const INCIDENT_TYPES = ['delay', 'breakdown', 'accident', 'passenger', 'luggage', 'other'] as const;
export type IncidentType = (typeof INCIDENT_TYPES)[number];

export interface Incident {
  readonly id: number;
  readonly route_code: string;
  readonly origin: string;
  readonly destination: string;
  readonly departure_id: number | null;
  readonly departs_at: string | null;
  readonly type: IncidentType;
  readonly description: string;
  readonly reported_by: string;
  readonly reported_at: string;
  readonly resolved_at: string | null;
  readonly resolution: string | null;
}

export interface DepartureDetail {
  readonly departure: Departure;
  readonly passengers: readonly Passenger[];
  readonly luggage: readonly Luggage[];
  readonly incidents: readonly Incident[];
}

export type TicketStatus = 'valid' | 'scanned' | 'already_scanned' | 'not_paid' | 'wrong_day' | 'other_station' | 'unknown';

export interface TicketCheck {
  readonly status: TicketStatus;
  readonly code: string;
  readonly given_name: string | null;
  readonly family_name: string | null;
  readonly reference: string | null;
  readonly origin: string | null;
  readonly destination: string | null;
  readonly departs_at: string | null;
  readonly last_departure_at: string | null;
  readonly scanned_at: string | null;
  readonly scanned_by: string | null;
}

export interface Schedule {
  readonly id: number;
  /** 1 = lundi … 7 = dimanche. */
  readonly day_of_week: number;
  /** « 07:00 », heure de Dakar. */
  readonly departs_at: string;
  readonly last_departure: string | null;
}

export interface Route {
  readonly id: number;
  readonly code: string;
  readonly network: 'interurban' | 'international' | 'airport';
  readonly origin: Station;
  readonly destination: Station;
  readonly price: string;
  readonly fee: string;
  readonly currency: string;
  readonly capacity: number;
  readonly active: boolean;
  readonly schedules: readonly Schedule[];
}

export interface DeskBooking {
  readonly reference: string;
  readonly status: 'held' | 'confirmed' | 'released';
  readonly origin: string;
  readonly destination: string;
  readonly departure_id: number;
  readonly departs_at: string;
  readonly email: string;
  readonly phone: string;
  readonly total: string;
  readonly currency: string;
  /** `card`, `mobile_money`, ou `null` : pas encore payée. */
  readonly payment_method: 'card' | 'mobile_money' | null;
  readonly payment_status: string | null;
  readonly created_at: string;
  readonly confirmed_at: string | null;
  readonly tickets: readonly { given_name: string; family_name: string; code: string | null; scanned_at: string | null }[];
}

/** Paiement mobile reçu après la revente des places : à rembourser à la main. */
export interface Refund {
  readonly id: number;
  readonly reference: string;
  readonly passengers: string;
  readonly email: string;
  readonly phone: string;
  readonly origin: string;
  readonly destination: string;
  readonly departs_at: string;
  readonly amount: string;
  readonly currency: string;
  readonly provider: string;
  readonly invoice_token: string;
  readonly receipt_url: string | null;
  readonly paid_at: string | null;
  readonly status: 'refund_needed' | 'refunded';
  readonly refunded_at: string | null;
  readonly refunded_by: string | null;
  readonly refund_note: string | null;
}
