import { IsoDate, PassengerCount } from '@shared/ui';
import { FlightOffer, FlightSearchSlices, SearchPassenger } from './flight.model';

export const CABIN_CLASSES = ['economy', 'premium', 'business', 'first'] as const;

export type TTripType = 'round' | 'oneway';

export type TCabinClass = (typeof CABIN_CLASSES)[number];

export type TLocationType = 'airport' | 'city';

export type TPassengerType = 'adult' | 'child' | 'infant_without_seat' | 'infant_with_seat';

export type TBaggageType = 'checked' | 'carry_on';

export type TAmenityCost = 'included' | 'paid' | 'n/a';

export type TLegroom = 'standard' | 'more' | 'less';

export type TSeatType = 'standard' | 'recliner' | 'flat' | 'flatbed';

export function normalizeCabin(value: string | undefined): TCabinClass {
  if (!value || !CABIN_CLASSES.includes(value as TCabinClass)) {
    return 'economy';
  }
  return value.toLowerCase() as TCabinClass;
}

export interface AirlineFilter {
  readonly code: string;
  readonly name: string;
  readonly logoUrl: string;
  readonly offerCount: number;
}

export interface BagFilter {
  cabin: boolean;
  hold: boolean;
}

export interface BudgetFilter {
  min: number;
  max: number;
}

export interface DateRangeValue {
  readonly start: IsoDate | null;
  readonly end: IsoDate | null;
}

/* ==========================================================================
 * Persisted search
 * ========================================================================== */
export interface IFlightSearch {
  tripType: TTripType;
  origin: string;
  destination: string;
  dates: DateRangeValue;
  passengers: PassengerCount;
  cabin: TCabinClass[];
}

export interface PersistedFlightSearch {
  readonly tripType: TTripType;
  readonly origin: string;
  readonly destination: string;
  readonly dates: DateRangeValue;
  readonly passengers: PassengerCount;
  readonly cabin: TCabinClass;
}

/* ==========================================================================
 * Search response
 * ========================================================================== */

export interface FlightSearchResult {
  readonly data: FlightSearchData;
}

export interface FlightSearchData {
  readonly id: string;
  readonly client_key: string;

  readonly airline_credit_ids: readonly string[];

  readonly cabin_class: TCabinClass;
  readonly live_mode: boolean;
  readonly created_at: string;

  readonly passengers: readonly SearchPassenger[];
  readonly slices: FlightSearchSlices;
  readonly offers: readonly FlightOffer[];
}
