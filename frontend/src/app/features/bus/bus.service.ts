import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { ICheckoutSession } from '@core/payment/payment.service';
import { IBusBooking, IBusDeparture, IBusPassenger, IBusPlace, IBusSearch } from '@shared/models/bus.model';

/** Places bloquées le temps du paiement, et la session Stripe à afficher. */
export interface IBusHold {
  readonly reference: string;
  /** Fin du blocage (UTC) : sans paiement d'ici là, les places sont rendues. */
  readonly hold_expires_at: string;
  readonly checkout: ICheckoutSession;
}

export interface IBusHoldRequest {
  /** Choisi avant l'envoi et rejoué tel quel : un nouvel essai retrouve la même réservation. */
  readonly request_id: string;
  readonly route_code: string;
  readonly departs_at: string;
  readonly passengers: readonly IBusPassenger[];
  readonly email: string;
  readonly phone: string;
  readonly return_path: string;
}

/** Billets de car : recherche, places bloquées et payées, page des billets. Montants fixés par le serveur. */
@Injectable({ providedIn: 'root' })
export class BusService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/transport`;
  private readonly transloco = inject(TranslocoService);

  places(): Observable<IBusPlace[]> {
    return this.http.get<IBusPlace[]>(`${this.url}/places`);
  }

  departures(search: IBusSearch): Observable<IBusDeparture[]> {
    return this.http.get<IBusDeparture[]>(`${this.url}/departures`, {
      params: { from: search.from, to: search.to, date: search.date, passengers: search.passengers },
    });
  }

  hold(request: IBusHoldRequest): Observable<IBusHold> {
    return this.http.post<IBusHold>(`${this.url}/bookings`, { ...request, locale: this.transloco.getActiveLang() });
  }

  /** Paiement autorisé : places vendues, paiement encaissé, billets envoyés. Rejouable. */
  confirm(reference: string, checkoutSessionId: string): Observable<IBusBooking> {
    return this.http.post<IBusBooking>(`${this.url}/bookings/${encodeURIComponent(reference)}/confirmation`, {
      checkout_session_id: checkoutSessionId,
    });
  }

  booking(reference: string, lastName: string): Observable<IBusBooking> {
    return this.http.get<IBusBooking>(`${this.url}/bookings/${encodeURIComponent(reference)}`, { params: { last_name: lastName } });
  }
}
