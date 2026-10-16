import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@core/config/app-config.token';

/** Session Checkout Stripe préparée par le backend, au montant qu'il a lui-même calculé. */
export interface ICheckoutSession {
  readonly checkout_session_id: string;
  readonly client_secret: string;
  readonly publishable_key: string;
  /** Montant débité, décimal (« 193.69 »), dans `currency` — la devise de l'offre. */
  readonly amount: string;
  readonly currency: string;
}

/** Paiements : le navigateur ne fournit jamais le montant, seulement ce qui est acheté. */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/payments`;

  /**
   * Billet + services choisis (bagages, un par siège), au prix de vente recalculé par le serveur.
   *
   * @param returnPath page où revenir après un moyen à redirection (chemin du site, ex. `/flight/b2Zm…`)
   * @param attempt    rang du paiement : le même rouvre la même session, un nouveau en ouvre une autre
   */
  flightCheckout(
    offerId: string,
    services: readonly { id: string; quantity: number }[],
    returnPath: string,
    attempt: number,
  ): Observable<ICheckoutSession> {
    return this.http.post<ICheckoutSession>(`${this.url}/flights/${encodeURIComponent(offerId)}/checkout-session`, {
      services,
      return_path: returnPath,
      attempt,
    });
  }

  /** Séjour : devis Duffel + frais de service BIP, montant recalculé par le serveur. */
  stayCheckout(quoteId: string, returnPath: string, attempt: number): Observable<ICheckoutSession> {
    return this.http.post<ICheckoutSession>(`${this.url}/stays/${encodeURIComponent(quoteId)}/checkout-session`, {
      return_path: returnPath,
      attempt,
    });
  }
}
