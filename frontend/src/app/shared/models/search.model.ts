import { IsoDate, PassengerCount } from '@shared/ui';
import { FlightOffer, FlightSearchSlices, SearchPassenger } from './flight.model';

export type TripType = 'round' | 'oneway';

export type CabinClass = 'economy' | 'premium' | 'business' | 'first';

export type LocationType = 'airport' | 'city';

export type PassengerType = 'adult' | 'child' | 'infant_without_seat' | 'infant_with_seat';

export type BaggageType = 'checked' | 'carry_on';

export type AmenityCost = 'included' | 'paid' | 'n/a';

export type Legroom = 'standard' | 'more' | 'less';

export type SeatType = 'standard' | 'recliner' | 'flat' | 'flatbed';

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
  tripType: TripType;
  origin: string;
  destination: string;
  dates: DateRangeValue;
  passengers: PassengerCount;
  cabin: CabinClass[];
}

export interface PersistedFlightSearch {
  readonly tripType: TripType;
  readonly origin: string;
  readonly destination: string;
  readonly dates: DateRangeValue;
  readonly passengers: PassengerCount;
  readonly cabin: CabinClass;
}

const CABIN_CLASSES: readonly CabinClass[] = ['economy', 'premium', 'business', 'first'];

export function normalizeCabin(value: string | undefined): CabinClass {
  if (!value || !CABIN_CLASSES.includes(value as CabinClass)) {
    return 'economy';
  }
  return value.toLowerCase() as CabinClass;
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

  readonly cabin_class: CabinClass;
  readonly live_mode: boolean;
  readonly created_at: string;

  readonly passengers: readonly SearchPassenger[];
  readonly slices: FlightSearchSlices;
  readonly offers: readonly FlightOffer[];
}
