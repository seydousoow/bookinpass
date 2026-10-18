import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { APP_CONFIG } from '@core/config/app-config.token';

/** Accès à une réservation : PNR + nom de famille d'un voyageur (vérifiés par le serveur à chaque appel). */
export interface IBookingAccess {
  readonly booking_reference: string;
  readonly last_name: string;
}

interface IPlace {
  readonly iata_code: string;
  readonly name?: string;
  readonly city_name?: string | null;
}

export interface IBookedSegment {
  readonly id?: string;
  readonly origin: IPlace;
  readonly destination: IPlace;
  /** Heures locales des aéroports, sans fuseau (`2026-12-10T10:15:00`). */
  readonly departing_at: string;
  readonly arriving_at: string;
  readonly marketing_carrier?: { readonly name?: string; readonly iata_code?: string };
  readonly marketing_carrier_flight_number?: string;
}

/** Commande Duffel (`GET /air/orders/{id}`), réduite à ce que la page affiche. */
export interface IBookedOrder {
  readonly id: string;
  readonly booking_reference: string;
  readonly cancelled_at?: string | null;
  readonly available_actions?: readonly string[];
  readonly slices: readonly { readonly origin: IPlace; readonly destination: IPlace; readonly segments: readonly IBookedSegment[] }[];
  readonly passengers: readonly { readonly id?: string; readonly given_name: string; readonly family_name: string; readonly type?: string }[];
}

export type TBookingStatus = 'confirmed' | 'changed' | 'cancellation_pending' | 'cancelled';

export interface IBookingView {
  readonly order: IBookedOrder;
  readonly status: TBookingStatus;
  /** Payé par le voyageur (frais compris), dans `paid_currency`. */
  readonly paid_amount: string;
  readonly paid_currency: string;
}

/** Devis d'annulation : remboursement annoncé par la compagnie, valable jusqu'à `expires_at`. */
export interface ICancellationQuote {
  readonly id: string;
  readonly refund_amount: string | null;
  readonly refund_currency: string | null;
  readonly refund_to?: string | null;
  readonly expires_at?: string | null;
}

/**
 * « Gérer mon vol ». L'accès (PNR + nom) n'est gardé qu'en mémoire, jamais dans le stockage du navigateur : un
 * rechargement de la page de la réservation renvoie au formulaire. Il est renvoyé à chaque appel, le serveur ne
 * faisant confiance à rien d'autre.
 */
@Injectable({ providedIn: 'root' })
export class ManageBookingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/flights/bookings`;
  /** Dernière réservation consultée : la page s'affiche sans second appel après le formulaire. */
  readonly current = signal<IBookingView | null>(null);
  private readonly _access = signal<IBookingAccess | null>(null);

  access(): IBookingAccess | null {
    return this._access();
  }

  forget(): void {
    this.current.set(null);
    this._access.set(null);
  }

  lookup(access: IBookingAccess): Observable<IBookingView> {
    return this.http.post<IBookingView>(`${this.url}/lookup`, access).pipe(
      tap((view) => {
        this.current.set(view);
        this._access.set({ booking_reference: access.booking_reference, last_name: access.last_name });
      }),
    );
  }

  quoteCancellation(access: IBookingAccess): Observable<ICancellationQuote> {
    return this.http.post<ICancellationQuote>(`${this.url}/cancellation`, access);
  }

  confirmCancellation(access: IBookingAccess, cancellationId: string): Observable<IBookingView> {
    return this.http
      .post<IBookingView>(`${this.url}/cancellation/confirm`, { ...access, cancellation_id: cancellationId })
      .pipe(tap((view) => this.current.set(view)));
  }
}

/** Code d'erreur métier du serveur (`booking-not-found`…), sinon `unavailable`. */
export function errorCode(error: unknown): string {
  const code = (error as { error?: { code?: unknown } } | null)?.error?.code;
  return typeof code === 'string' ? code : 'unavailable';
}
