import { TAmenityCost, TBaggageType, TCabinClass, TLegroom, TLocationType, TPassengerType, TSeatType } from './search.model';

export type TMealType =
  | 'baby_meal'
  | 'bland_meal'
  | 'asian_vegetarian_meal'
  | 'diabetic_meal'
  | 'gluten_free_meal'
  | 'hindu_meal'
  | 'kosher_meal'
  | 'muslim_meal'
  | 'vegan_meal'
  | 'vegetarian_lacto_ovo_meal'
  | 'traditional_meal'
  | 'low_fat_meal'
  | 'low_salt_meal'
  | 'lactose_free_meal'
  | 'healthy_meal'
  | 'swiss_cold_meal'
  | 'swiss_brunch'
  | 'japanese_meal'
  | 'child_meal'
  | 'allergen_meal'
  | 'vegetarian_meal'
  | 'meal';

export type TServiceType = 'baggage' | 'seat' | 'meal' | 'cancel_for_any_reason';

export type IServiceMetadata = IBaggageServiceMetadata | ISeatServiceMetadata | IMealServiceMetadata | ICancelForAnyReasonServiceMetadata;

export interface IBaggageServiceMetadata {
  readonly maximum_depth_cm: number | null;
  readonly maximum_height_cm: number | null;
  readonly maximum_length_cm: number | null;
  readonly maximum_weight_kg: number | null;
  readonly type: TBaggageType;
}

export interface ISeatServiceMetadata {
  readonly designator: string;
  readonly disclosures: readonly string[];
  readonly name: string;
}

export interface IMealServiceMetadata {
  readonly meal: TMealType;
}

export interface ICancelForAnyReasonServiceMetadata {
  readonly merchant_copy: string;
  readonly refund_amount: string;
  readonly terms_and_conditions_url: string;
}

/* ==========================================================================
 * Offer
 * ========================================================================== */
export interface IFlightOffer {
  readonly id: string;

  readonly created_at: string;
  readonly updated_at: string;
  readonly expires_at: string;

  readonly live_mode: boolean;

  readonly supported_loyalty_programmes: readonly string[];

  readonly base_amount: string;
  readonly intended_base_amount: string;
  readonly base_currency: string;

  readonly tax_amount: string | null;
  readonly tax_currency: string | null;

  readonly total_amount: string;
  readonly intended_total_amount: string;
  readonly total_currency: string;

  readonly total_emissions_kg: string | null;

  readonly passenger_identity_documents_required: boolean;
  readonly supported_passenger_identity_document_types: ('passport' | 'known_traveler_number' | 'passenger_redress_number' | 'tax_id')[];

  readonly partial: boolean;

  readonly private_fares: readonly IPrivateFare[];

  readonly conditions: IOfferConditions;
  readonly owner: IAirline;

  readonly slices: readonly IOfferSlice[];
  readonly passengers: readonly IOfferPassenger[];

  readonly payment_requirements: IPaymentRequirements;
  readonly intended_services: readonly IIntendedService[] | null;

  readonly available_services: readonly IService[] | null;
  readonly available_airline_credit_ids: readonly string[];
}

export interface ITaxBreakdown {
  readonly amount: string;
  readonly carrier_imposed: boolean;
  readonly code: string;
  readonly name: string;
  readonly description: string;
  readonly currency: string;
}

export interface IPrivateFare {
  readonly corporate_code: string;
  readonly tour_code: string;
  readonly tracking_reference: string;
  readonly type: 'corporate' | 'leisure' | 'negotiated';
}

export interface IIntendedService {
  readonly id: string;
  readonly quantity: number;
}

export type TPaymentMethodType = 'card' | 'airline_credit';

export type TIntendedPaymentMethod = IIntendedCardPaymentMethod | IIntendedAirlineCreditPaymentMethod;

export interface IIntendedCardPaymentMethod {
  readonly type: TPaymentMethodType;

  readonly card_id: string;

  readonly charge_amount: string;
  readonly charge_currency: string;

  readonly surcharge_amount: string;
  readonly surcharge_currency: string;
}

export interface IIntendedAirlineCreditPaymentMethod {
  readonly type: TPaymentMethodType;

  readonly airline_credit_id: string;

  readonly charge_amount: string;
  readonly charge_currency: string;

  readonly penalty_amount: string;
  readonly penalty_currency: string;
}

export interface IService {
  readonly id: string;
  readonly maximum_quantity: number;
  readonly metadata: IServiceMetadata;
  readonly passenger_ids: string[];
  readonly segment_ids: string[];
  readonly total_amount: string;
  readonly total_currency: string;
  readonly type: TServiceType;
}

export interface IPaymentRequirements {
  readonly requires_instant_payment: boolean;
  readonly payment_required_by: string | null;
  readonly price_guarantee_expires_at: string | null;
}

export interface IOfferSlice {
  readonly id: string;

  readonly origin: IFlightLocation;
  readonly origin_type: TLocationType;

  readonly destination: IFlightLocation;
  readonly destination_type: TLocationType;

  readonly duration: string;
  readonly fare_brand_name: string | null;

  readonly comparison_key: string;
  readonly conditions: IOfferSliceConditions;

  readonly ngs_shelf: number | null;
  /*1: standard seating with limited extras.

2: standard seating with extras, i.e. carry-on baggage, advanced seat selection, priority boarding etc.

3: preferred seating, such as additional legroom, seat width or middle seat free

4: premium seating, additional legroom and recline. Situated in business class or higher.

5: deluxe seating, additional legroom and reclines to lie flat position. Situated in business class or higher.*/

  readonly segments: readonly IFlightSegment[];
}

/* ==========================================================================
 * Conditions
 * ========================================================================== */

export interface IOfferSliceConditions {
  readonly change_before_departure: ICondition | null;
  readonly advance_seat_selection: boolean | null;
  readonly priority_boarding: boolean | null;
  readonly priority_check_in: boolean | null;
}

export interface IOfferConditions {
  readonly change_before_departure: ICondition | null;
  readonly refund_before_departure: ICondition | null;
}

export interface ICondition {
  readonly allowed: boolean;
  readonly penalty_amount: string | null;
  readonly penalty_currency: string | null;
}

/* ==========================================================================
 * Segment
 * ========================================================================== */

export interface IFlightSegment {
  readonly aircraft: IAircraft | null;
  readonly departing_at: string;
  readonly arriving_at: string;
  readonly destination: ISegmentLocation;
  readonly destination_terminal: string | null;

  readonly id: string;

  readonly duration: string;
  readonly distance: number | null;

  readonly marketing_carrier: IAirline;
  readonly operating_carrier: IAirline;

  readonly origin_terminal: string | null;

  readonly marketing_carrier_flight_number: string;
  readonly operating_carrier_flight_number: string;

  readonly media: readonly unknown[];

  readonly origin: IFlightLocation;

  readonly passengers: readonly ISegmentPassenger[];
  readonly stops: readonly ISegmentStop[];
}

/* ==========================================================================
 * Stops
 * ========================================================================== */

export interface ISegmentStop {
  readonly id: string;

  readonly duration: string;
  readonly departing_at: string;
  readonly arriving_at: string;

  readonly airport: IFlightLocation;
}

/* ==========================================================================
 * Passengers
 * ========================================================================== */

export interface ISegmentPassenger {
  readonly passenger_id: string;
  readonly cabin_class: TCabinClass;
  readonly cabin_class_marketing_name: string;
  readonly fare_basis_code: string;
  readonly baggages: readonly IBaggage[];
  readonly cabin: ICabin;
}

export interface IOfferPassenger {
  readonly age: number | null;
  readonly family_name: string | null;
  readonly fare_type: string | null;
  readonly given_name: string | null;
  readonly id: string;
  readonly loyalty_programme_accounts: readonly ILoyaltyProgrammeAccount[];
  readonly type: TPassengerType | null;
}

/* ==========================================================================
 * Baggage
 * ========================================================================== */
export interface IBaggage {
  readonly type: TBaggageType;
  readonly quantity: number;
}

/* ==========================================================================
 * Cabin
 * ========================================================================== */
export interface ICabin {
  readonly marketing_name: string;
  readonly name: TCabinClass;
  readonly amenities: ICabinAmenities | null;
}

export interface ICabinAmenities {
  readonly wifi: IWifiAmenity;
  readonly seat: ISeatAmenity;
  readonly power: IPowerAmenity;
}

export interface IWifiAmenity {
  readonly available: boolean;
  readonly cost: TAmenityCost;
}

export interface ISeatAmenity {
  readonly type: TSeatType | null;
  readonly legroom: TLegroom | null;
  readonly pitch: string | null;
}

export interface IPowerAmenity {
  readonly available: boolean;
}

/* ==========================================================================
 * Aircraft
 * ========================================================================== */

export interface IAircraft {
  readonly iata_code: string;
  readonly id: string;
  readonly name: string;
}

/* ==========================================================================
 * Airline
 * ========================================================================== */
export interface IAirline {
  readonly conditions_of_carriage_url: string | null;

  readonly iata_code: string | null;
  readonly id: string;

  readonly logo_lockup_url: string | null;
  readonly logo_symbol_url: string | null;

  readonly name: string;
}

/* ==========================================================================
 * Location
 * ========================================================================== */
export interface IFlightLocation extends ICityLocation {
  readonly city: ICityLocation | null;
  readonly airports: ICityLocation[] | null;
}

export interface ICityLocation extends IBaseLocation {
  readonly type: TLocationType;
}

export interface ILoyaltyProgrammeAccount {
  airline_iata_code: string;
  account_number: string;
}

export interface ISegmentLocation extends IBaseLocation {
  readonly city: ICity;
}

export interface IBaseLocation extends ICity {
  readonly city_name: string;
  readonly iata_city_code: string;
  readonly icao_code: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly time_zone: string;
}

export interface ICity {
  readonly id: string;
  readonly name: string;
  readonly iata_code: string;
  readonly iata_country_code: string;
}
