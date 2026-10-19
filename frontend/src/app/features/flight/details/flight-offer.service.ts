import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { IFlightOfferDetails, IFlightOfferResponse, IIntendedService, IPassengerUpdate } from '@shared/models';
import { IIdentityDocument } from './traveler-form/traveler-form';

/** Voyageur tel que Duffel le réserve (`POST /flights/orders`). */
export interface IOrderPassenger {
  readonly id: string;
  readonly title: string;
  readonly gender: string;
  readonly given_name: string;
  readonly family_name: string;
  readonly born_on: string;
  readonly identity_documents?: readonly IIdentityDocument[];
}

/** Réservation d'une offre payée : le serveur relit la session Checkout chez Stripe (montant, services). */
export interface IFlightOrderRequest {
  readonly offer_id: string;
  readonly checkout_session_id: string;
  readonly email: string;
  /** E.164, ex. `+221771234567`. */
  readonly phone_number: string;
  readonly passengers: readonly IOrderPassenger[];
}

/** Réservation confirmée. */
export interface IFlightOrder {
  readonly order_id: string;
  /** Référence de la compagnie (PNR) : le voyageur gère sa réservation avec elle. */
  readonly booking_reference: string;
  /** Montant débité, décimal, dans `currency`. */
  readonly amount: string;
  readonly currency: string;
  /** Compagnies qui opèrent les vols : à afficher sur la confirmation (règle américaine). */
  readonly operating_carriers: readonly string[];
}

/** Écritures sur une offre. Les lectures passent par `httpResource` dans les composants. */
@Injectable({ providedIn: 'root' })
export class FlightOfferService {
  private readonly http = inject(HttpClient);
  private readonly offersUrl = `${inject(APP_CONFIG).apiBaseUrl}/flights/offers`;
  private readonly ordersUrl = `${inject(APP_CONFIG).apiBaseUrl}/flights/orders`;
  private readonly transloco = inject(TranslocoService);

  /** Offre complète, avec ses services disponibles. */
  getOffer(offerId: string): Observable<IFlightOfferDetails> {
    return this.http.get<IFlightOfferResponse>(`${this.offersUrl}/${encodeURIComponent(offerId)}`).pipe(map((r) => r.data));
  }

  /**
   * Tarification finale (étape de paiement) : Duffel rend l'offre à jour, dont le `total_amount` comprend les services
   * prévus (bagages, sièges) et d'éventuels suppléments. Elle remplace l'offre courante.
   */
  price(offerId: string, services: readonly IIntendedService[]): Observable<IFlightOfferDetails> {
    return this.http
      .post<IFlightOfferResponse>(`${this.offersUrl}/${encodeURIComponent(offerId)}/price`, { intended_services: services })
      .pipe(map((r) => r.data));
  }

  /** Le backend relit l'offre après la mise à jour (le prix peut changer) : c'est elle qui est rendue. */
  updatePassenger(offerId: string, passengerId: string, update: IPassengerUpdate): Observable<IFlightOfferDetails> {
    const url = `${this.offersUrl}/${encodeURIComponent(offerId)}/passengers/${encodeURIComponent(passengerId)}`;
    return this.http.patch<IFlightOfferResponse>(url, update).pipe(map((r) => r.data));
  }

  /** Réserve l'offre payée ; rejouable sans risque (le serveur rend la réservation déjà faite). */
  /** La langue active accompagne la commande : les e-mails de la réservation (SES) sont envoyés dans cette langue. */
  createOrder(request: IFlightOrderRequest): Observable<IFlightOrder> {
    return this.http.post<IFlightOrder>(this.ordersUrl, { ...request, lang: this.transloco.getActiveLang() });
  }
}
