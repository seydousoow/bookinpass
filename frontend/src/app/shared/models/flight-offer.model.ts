import {
  IAirline,
  IIntendedService,
  IOfferConditions,
  IOfferPassenger,
  IOfferSlice,
  IPaymentRequirements,
  IPrivateFare,
  IService,
  ITaxBreakdown,
  TIntendedPaymentMethod,
} from '@shared/models/flight.model';

export interface IFlightOfferResponse {
  data: IFlightOfferDetails;
}

export interface IFlightOfferDetails {
  readonly available_airline_credit_ids: string[];
  readonly available_services: readonly IService[] | null;

  readonly base_amount: string;
  readonly base_currency: string;

  readonly id: string;
  readonly live_mode: boolean;
  readonly created_at: string;
  readonly updated_at: string;
  readonly expires_at: string;

  readonly conditions: IOfferConditions;

  readonly intended_services: readonly IIntendedService[] | null;
  readonly intended_payment_methods: readonly TIntendedPaymentMethod[];

  readonly owner: IAirline;

  readonly passengers: readonly IOfferPassenger[];
  readonly partial: boolean;
  readonly payment_requirements: IPaymentRequirements;
  readonly passenger_identity_documents_required: boolean;
  readonly private_fares: readonly IPrivateFare[];

  readonly slices: readonly IOfferSlice[];

  readonly supported_passenger_identity_document_types: ('passport' | 'known_traveler_number' | 'passenger_redress_number' | 'tax_id')[];
  readonly supported_loyalty_programmes: readonly string[];

  readonly total_amount: string;
  readonly total_currency: string;

  readonly tax_amount: string | null;
  readonly tax_currency: string | null;
  readonly tax_breakdown: readonly ITaxBreakdown[];

  readonly total_emissions_kg: string | null;
}
