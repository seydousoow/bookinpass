import { TAmenityCost, TBaggageType, TCabinClass, TLegroom, TLocationType, TPassengerType, TSeatType } from './search.model';

/**
 * The requested journey.
 *
 * One-way:
 *   [outbound]
 *
 * Round-trip:
 *   [outbound, inbound]
 */
export type FlightSearchSlices = readonly [OutboundSearchSlice] | readonly [OutboundSearchSlice, InboundSearchSlice];

export interface OutboundSearchSlice {
  readonly destination_type: TLocationType;
  readonly origin_type: TLocationType;
  readonly departure_date: string;

  readonly origin: FlightLocation;
  readonly destination: FlightLocation;
}

export interface InboundSearchSlice {
  readonly destination_type: TLocationType;
  readonly origin_type: TLocationType;
  readonly departure_date: string;

  readonly origin: FlightLocation;
  readonly destination: FlightLocation;
}

/* ==========================================================================
 * Offer
 * ========================================================================== */

export interface FlightOffer {
  readonly id: string;

  readonly created_at: string;
  readonly updated_at: string;
  readonly expires_at: string;

  readonly live_mode: boolean;

  readonly supported_loyalty_programmes: readonly string[];

  readonly base_amount: string;
  readonly intended_base_amount: string;
  readonly base_currency: string;

  readonly tax_amount: string;
  readonly tax_currency: string;

  readonly total_amount: string;
  readonly intended_total_amount: string;
  readonly total_currency: string;

  readonly total_emissions_kg: string;

  readonly passenger_identity_documents_required: boolean;

  readonly supported_passenger_identity_document_types: readonly string[];

  readonly partial: boolean;

  readonly private_fares: readonly unknown[];

  readonly conditions: OfferConditions;

  readonly owner: Airline;

  readonly slices: FlightOfferSlices;

  readonly passengers: readonly OfferPassenger[];

  readonly available_services: readonly unknown[] | null;

  readonly payment_requirements: PaymentRequirements;

  readonly intended_services: readonly unknown[] | null;

  readonly available_airline_credit_ids: readonly string[];
}

/**
 * Same principle as the search-level slices:
 *
 * One-way:
 *   [outbound]
 *
 * Round-trip:
 *   [outbound, inbound]
 *
 * We keep the tuple because the API semantics guarantee the number
 * of slices according to the journey type.
 */
export interface PaymentRequirements {
  readonly requires_instant_payment: boolean;
  readonly payment_required_by: string | null;
  readonly price_guarantee_expires_at: string | null;
}
export type FlightOfferSlices = readonly [OutboundOfferSlice] | readonly [OutboundOfferSlice, InboundOfferSlice];

export interface OutboundOfferSlice {
  readonly id: string;
  readonly duration: string;

  readonly destination_type: TLocationType;
  readonly origin_type: TLocationType;

  readonly fare_brand_name: string | null;
  readonly ngs_shelf: number | null;

  readonly conditions: OfferSliceConditions;
  readonly comparison_key: string;

  readonly origin: FlightLocation;
  readonly destination: FlightLocation;

  readonly segments: readonly FlightSegment[];
}

export interface InboundOfferSlice {
  readonly id: string;
  readonly duration: string;

  readonly destination_type: TLocationType;
  readonly origin_type: TLocationType;

  readonly fare_brand_name: string | null;
  readonly ngs_shelf: number | null;

  readonly conditions: OfferSliceConditions;
  readonly comparison_key: string;

  readonly origin: FlightLocation;
  readonly destination: FlightLocation;

  readonly segments: readonly FlightSegment[];
}

/* ==========================================================================
 * Offer slice
 * ========================================================================== */

export interface OfferSliceConditions {
  readonly change_before_departure: ChangeCondition | null;
  readonly advance_seat_selection: boolean | null;
  readonly priority_boarding: boolean | null;
  readonly priority_check_in: boolean | null;
}

export interface OfferConditions {
  readonly change_before_departure: ChangeCondition | null;
  readonly refund_before_departure: RefundCondition | null;
}

export interface ChangeCondition {
  readonly allowed: boolean;
  readonly penalty_amount?: string;
  readonly penalty_currency?: string;
}

export interface RefundCondition {
  readonly allowed: boolean;
  readonly penalty_amount?: string;
  readonly penalty_currency?: string;
}

/* ==========================================================================
 * Segment
 * ========================================================================== */

export interface FlightSegment {
  readonly id: string;

  readonly departing_at: string;
  readonly arriving_at: string;

  readonly duration: string;
  readonly distance: number | null;

  readonly origin_terminal: string | null;
  readonly destination_terminal: string | null;

  readonly marketing_carrier_flight_number: string;
  readonly operating_carrier_flight_number: string;

  readonly media: readonly unknown[];

  readonly origin: FlightLocation;
  readonly destination: FlightLocation;

  readonly passengers: readonly SegmentPassenger[];
  readonly stops: readonly FlightStop[];

  readonly aircraft: Aircraft;

  readonly marketing_carrier: Airline;
  readonly operating_carrier: Airline;
}

/* ==========================================================================
 * Stops
 * ========================================================================== */

export interface FlightStop {
  readonly id: string;

  readonly duration: string;
  readonly departing_at: string;
  readonly arriving_at: string;

  readonly airport: FlightLocation;
}

/* ==========================================================================
 * Passengers
 * ========================================================================== */

export interface SearchPassenger {
  readonly id: string;
  readonly type: TPassengerType | null;

  readonly fare_type: string | null;
  readonly age: number | null;

  readonly given_name: string | null;
  readonly family_name: string | null;
  readonly user_id: string | null;

  readonly loyalty_programme_accounts: readonly string[];
}

export interface OfferPassenger {
  readonly passenger_id: string;

  readonly cabin_class: TCabinClass;
  readonly cabin_class_marketing_name: string;

  readonly fare_basis_code: string;

  readonly baggages: readonly Baggage[];

  readonly cabin: Cabin;
}

export interface SegmentPassenger {
  readonly passenger_id: string;

  readonly cabin_class: TCabinClass;
  readonly cabin_class_marketing_name: string;

  readonly fare_basis_code: string;

  readonly baggages: readonly Baggage[];

  readonly cabin: Cabin;
}

/* ==========================================================================
 * Baggage
 * ========================================================================== */

export interface Baggage {
  readonly type: TBaggageType;
  readonly quantity: number;
}

/* ==========================================================================
 * Cabin
 * ========================================================================== */

export interface Cabin {
  readonly marketing_name: string;
  readonly name: TCabinClass;

  readonly amenities: CabinAmenities;
}

export interface CabinAmenities {
  readonly wifi: WifiAmenity;
  readonly seat: SeatAmenity;
  readonly power: PowerAmenity;
}

export interface WifiAmenity {
  readonly available: boolean;
  readonly cost: TAmenityCost;
}

export interface SeatAmenity {
  readonly type: TSeatType;
  readonly legroom: TLegroom;
  readonly pitch: string;
}

export interface PowerAmenity {
  readonly available: boolean;
}

/* ==========================================================================
 * Aircraft
 * ========================================================================== */

export interface Aircraft {
  readonly iata_code: string;
  readonly id: string;
  readonly name: string;
}

/* ==========================================================================
 * Airline
 * ========================================================================== */

export interface Airline {
  readonly conditions_of_carriage_url: string;

  readonly iata_code: string;
  readonly id: string;

  readonly logo_lockup_url: string;
  readonly logo_symbol_url: string;

  readonly name: string;
}

/* ==========================================================================
 * Location
 * ========================================================================== */

export interface FlightLocation {
  readonly id: string;

  readonly type: TLocationType;
  readonly name: string;

  readonly iata_code: string;
  readonly iata_city_code: string;
  readonly iata_country_code: string;

  readonly icao_code: string;

  readonly city_name: string;

  readonly latitude: number;
  readonly longitude: number;

  readonly time_zone: string;

  readonly city: CityLocation | null;
}

export interface CityLocation {
  readonly id: string;

  readonly type: TLocationType;
  readonly name: string;

  readonly iata_code: string;
  readonly iata_city_code: string;
  readonly iata_country_code: string;

  readonly city_name: string | null;
  readonly icao_code: string | null;

  readonly latitude: number | null;
  readonly longitude: number | null;

  readonly time_zone: string | null;
}
