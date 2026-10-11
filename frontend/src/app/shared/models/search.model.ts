import { IPassengerCount, IsoDate } from '@shared/ui';
import { IFlightLocation, IFlightOffer, ILoyaltyProgrammeAccount } from './flight.model';

export const CABIN_CLASSES = ['economy', 'premium_economy', 'business', 'first'] as const;

export type TTripType = 'round' | 'oneway';

export type TCabinClass = (typeof CABIN_CLASSES)[number];

export type TLocationType = 'airport' | 'city';

export type TPassengerType = 'adult' | 'child' | 'infant_without_seat';

export type TBaggageType = 'checked' | 'carry_on';

export type TAmenityCost = 'included' | 'paid' | 'n/a' | 'free' | 'free or paid';

export type TLegroom = 'standard' | 'more' | 'less' | 'n/a';

export type TSeatType = "standard" | "skycouch" | "recliner" | "angle_flat" | "full_flat" | "full_flat_pod" | "private_suite";

export function normalizeCabin(value: string | undefined): TCabinClass {
  if (!value || !CABIN_CLASSES.includes(value as TCabinClass)) {
    return 'economy';
  }
  return value.toLowerCase() as TCabinClass;
}

export interface IAirlineFilter {
  readonly code: string;
  readonly name: string;
  readonly logoUrl: string;
  readonly offerCount: number;
}

export interface IBagFilter {
  cabin: boolean;
  hold: boolean;
}

export interface IDateRangeValue {
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
  dates: IDateRangeValue;
  passengers: IPassengerCount;
  cabin: TCabinClass[];
}

export interface IPersistedFlightSearch {
  readonly tripType: TTripType;
  readonly origin: string;
  readonly destination: string;
  readonly dates: IDateRangeValue;
  readonly passengers: IPassengerCount;
  readonly cabin: TCabinClass;
}

/* ==========================================================================
 * Search response
 * ========================================================================== */

export interface IFlightSearchResult {
  readonly data: IFlightSearchData;
}

export interface IFlightSearchData {
  readonly airline_credit_ids: readonly string[];
  readonly cabin_class: TCabinClass;
  readonly client_key: string;
  readonly created_at: string;

  readonly live_mode: boolean;
  readonly id: string;

  readonly passengers: readonly ISearchPassenger[];
  readonly slices: readonly [ISearchSlice] | readonly [ISearchSlice, ISearchSlice];
  readonly offers: readonly IFlightOffer[];
}

export interface ISearchSlice {
  readonly destination_type: TLocationType;
  readonly origin_type: TLocationType;
  readonly departure_date: string;

  readonly origin: IFlightLocation;
  readonly destination: IFlightLocation;
}

export interface ISearchPassenger {
  readonly id: string;
  readonly type: TPassengerType | null;

  readonly fare_type: string | null;
  readonly age: number | null;

  readonly given_name: string | null;
  readonly family_name: string | null;
  readonly user_id: string | null;

  readonly loyalty_programme_accounts: readonly string[] | readonly ILoyaltyProgrammeAccount[];
}
