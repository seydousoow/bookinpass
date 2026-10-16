import { IDateRangeValue } from './search.model';

/* ==========================================================================
 * Recherche (formulaire et URL)
 * ========================================================================== */

/** Voyageurs d'une recherche d'hébergement : l'âge de chaque enfant est exigé par Duffel. */
export interface IStayGuests {
  readonly rooms: number;
  readonly adults: number;
  /** Âge de chaque enfant (0 à 17 ans). */
  readonly childAges: readonly number[];
}

export interface IStaySearch {
  /** Identifiant GeoNames de la ville (`/stays/destinations`) ; le backend le traduit en coordonnées. */
  location: string;
  dates: IDateRangeValue;
  guests: IStayGuests;
}

/** Ville proposée par `GET /stays/destinations` (référentiel GeoNames). */
export interface IStayDestination {
  readonly id: number;
  readonly name: string;
  /** Code pays ISO 3166-1 alpha-2 : son nom s'affiche dans la langue active (`Intl.DisplayNames`). */
  readonly countryCode: string;
}

/* ==========================================================================
 * Réponses Duffel Stays (relayées telles quelles par le backend)
 * ========================================================================== */

export type TStayAmenity =
  | 'parking'
  | 'gym'
  | 'wifi'
  | '24_hour_front_desk'
  | 'accessibility_hearing'
  | 'accessibility_mobility'
  | 'adult_only'
  | 'business_centre'
  | 'cash_machine'
  | 'childcare_service'
  | 'concierge'
  | 'laundry'
  | 'lounge'
  | 'pets_allowed'
  | 'pool'
  | 'restaurant'
  | 'room_service'
  | 'spa';

export type TBedType = 'single' | 'double' | 'queen' | 'king' | 'sofabed' | 'bunk';
export type TBoardType = 'room_only' | 'breakfast' | 'half_board' | 'full_board' | 'all_inclusive';
export type TStayPaymentType = 'pay_now' | 'guarantee' | 'deposit';
export type TRatingSource = 'priceline' | 'aaa' | 'northstar' | 'bookingcom' | 'expedia';
export type TBenefitType = 'breakfast_included' | 'property_credit' | 'room_upgrade' | 'early_check_in' | 'late_check_out';

export interface IStayPhoto {
  readonly url: string;
}

export interface IStayAddress {
  readonly line_one: string;
  readonly city_name: string;
  readonly postal_code: string;
  readonly region: string | null;
  readonly country_code: string;
}

export interface IStayLocation {
  readonly address: IStayAddress;
  readonly geographic_coordinates: { readonly latitude: number; readonly longitude: number };
}

export interface IStayCheckInInformation {
  readonly check_in_after_time: string | null;
  readonly check_in_before_time: string | null;
  readonly check_out_before_time: string | null;
}

export interface IStayRating {
  readonly source: TRatingSource;
  readonly value: number;
}

export interface IStayRefund {
  /** Date limite (ISO, UTC ou avec décalage). */
  readonly before: string;
  readonly refund_amount: string;
  readonly currency: string;
}

export interface IStayCondition {
  readonly title: string;
  /** Peut contenir du HTML (liens) : affiché comme texte, jamais interprété. */
  readonly description: string;
}

export interface IStayBenefit {
  readonly type: TBenefitType;
  readonly title: string;
  readonly description: string;
}

export interface IStayRate {
  readonly id: string;
  readonly name: string | null;
  readonly total_amount: string;
  readonly total_currency: string;
  readonly base_amount: string | null;
  readonly base_currency: string;
  readonly tax_amount: string | null;
  readonly tax_currency: string;
  readonly fee_amount: string | null;
  readonly fee_currency: string;
  readonly public_amount: string | null;
  readonly public_currency: string;
  /** Taxes et frais réglés sur place, hors `total_amount`. */
  readonly due_at_accommodation_amount: string | null;
  readonly due_at_accommodation_currency: string;
  readonly board_type: TBoardType | null;
  readonly payment_type: TStayPaymentType;
  readonly available_payment_methods: readonly string[];
  /** Triée par date croissante ; vide : non remboursable. */
  readonly cancellation_timeline: readonly IStayRefund[];
  readonly conditions: readonly IStayCondition[];
  readonly benefits: readonly IStayBenefit[] | null;
  readonly quantity_available: number | null;
  readonly loyalty_programme_required: boolean;
  readonly supported_loyalty_programme: string | null;
  readonly expires_at: string;
}

export interface IStayRoom {
  readonly name: string;
  readonly beds: readonly { readonly type: TBedType; readonly count: number }[] | null;
  readonly photos: readonly IStayPhoto[] | null;
  readonly rates: readonly IStayRate[];
}

export interface IAccommodation {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly photos: readonly IStayPhoto[];
  /** Étoiles consolidées par Duffel (1 à 5). */
  readonly rating: number | null;
  readonly ratings: readonly IStayRating[] | null;
  /** Note des avis clients (1 à 10). */
  readonly review_score: number | null;
  readonly review_count: number | null;
  readonly amenities: readonly { readonly type: TStayAmenity; readonly description: string }[] | null;
  readonly chain: { readonly id: string; readonly name: string } | null;
  readonly brand: { readonly id: string; readonly name: string } | null;
  readonly check_in_information: IStayCheckInInformation | null;
  readonly key_collection: { readonly instructions: string } | null;
  readonly location: IStayLocation;
  readonly rooms: readonly IStayRoom[];
  readonly supported_loyalty_programme: string | null;
}

export interface IStayGuest {
  readonly type: 'adult' | 'child';
  readonly age?: number | null;
}

/** Établissement disponible pour les dates cherchées. */
export interface IStayResult {
  readonly id: string;
  readonly accommodation: IAccommodation;
  readonly check_in_date: string;
  readonly check_out_date: string;
  readonly rooms: number;
  readonly guests: readonly IStayGuest[] | null;
  readonly expires_at: string;
  readonly cheapest_rate_total_amount: string;
  readonly cheapest_rate_currency: string;
  readonly cheapest_rate_public_amount: string | null;
  readonly cheapest_rate_due_at_accommodation_amount: string | null;
  readonly cheapest_rate_due_at_accommodation_currency: string | null;
}

export interface IStaySearchResponse {
  readonly data: { readonly results: readonly IStayResult[]; readonly created_at: string | null };
}

export interface IStayResultResponse {
  readonly data: IStayResult;
}

/** Vérification finale d'un tarif : prix définitif et acompte éventuel. */
export interface IStayQuote {
  readonly id: string;
  readonly accommodation: IAccommodation;
  readonly check_in_date: string;
  readonly check_out_date: string;
  readonly rooms: number;
  readonly guests: readonly IStayGuest[] | null;
  readonly total_amount: string;
  readonly total_currency: string;
  readonly base_amount: string | null;
  readonly tax_amount: string | null;
  readonly fee_amount: string | null;
  readonly due_at_accommodation_amount: string | null;
  readonly due_at_accommodation_currency: string;
  /** Part payée avant l'arrivée pour un tarif `deposit`. */
  readonly deposit_amount: string | null;
  readonly deposit_currency: string;
}

export interface IStayQuoteResponse {
  readonly data: IStayQuote;
}

export interface IStayReview {
  readonly reviewer_name: string;
  readonly score: number;
  readonly text: string;
  readonly created_at: string;
}

export interface IStayReviewsResponse {
  readonly meta: { readonly limit: number; readonly after: string | null };
  readonly data: { readonly reviews: readonly IStayReview[] };
}
