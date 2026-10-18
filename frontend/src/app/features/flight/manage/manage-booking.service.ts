import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { APP_CONFIG } from '@core/config/app-config.token';
import { IFlightSegment, IOfferSlice } from '@shared/models';

/** Accès à une réservation : PNR + nom de famille d'un voyageur (vérifiés par le serveur à chaque appel). */
export interface IBookingAccess {
  readonly booking_reference: string;
  readonly last_name: string;
}

/** Condition du billet (`conditions.change_before_departure`…) ; `null` : inconnue de la compagnie. */
export interface IBookedCondition {
  readonly allowed: boolean;
  readonly penalty_amount: string | null;
  readonly penalty_currency: string | null;
}

export interface IBookedPassenger {
  readonly id: string;
  readonly title?: string | null;
  readonly given_name: string;
  readonly family_name: string;
  readonly type?: string | null;
  readonly infant_passenger_id?: string | null;
  readonly loyalty_programme_accounts?: readonly { readonly airline_iata_code: string; readonly account_number: string }[];
}

/** Siège retenu sur un segment (`segments[].passengers[].seat`). */
export interface IBookedSeat {
  readonly designator: string;
  readonly name?: string | null;
}

/** Service acheté (bagage, siège, repas…), rattaché à des segments et des passagers. */
export interface IBookedService {
  readonly id: string;
  readonly type: string;
  readonly quantity: number;
  readonly passenger_ids: readonly string[];
  readonly segment_ids: readonly string[];
  readonly metadata?: Readonly<Record<string, unknown>> | null;
}

export type IBookedSegment = Omit<IFlightSegment, 'passengers'> & {
  readonly passengers: readonly (IFlightSegment['passengers'][number] & { readonly seat?: IBookedSeat | null })[];
};

export type IBookedSlice = Omit<IOfferSlice, 'segments'> & { readonly segments: readonly IBookedSegment[] };

/** Commande Duffel (`GET /air/orders/{id}`), sans métadonnées ni clients (retirés par le serveur). */
export interface IBookedOrder {
  readonly id: string;
  readonly booking_reference: string;
  readonly cancelled_at?: string | null;
  readonly available_actions?: readonly string[];
  readonly airline_initiated_changes?: readonly unknown[];
  readonly owner?: { readonly name: string; readonly logo_symbol_url?: string | null };
  readonly slices: readonly IBookedSlice[];
  readonly passengers: readonly IBookedPassenger[];
  readonly documents?: readonly { readonly type: string; readonly unique_identifier: string; readonly passenger_ids: readonly string[] }[];
  readonly services?: readonly IBookedService[];
  readonly conditions?: {
    readonly change_before_departure?: IBookedCondition | null;
    readonly refund_before_departure?: IBookedCondition | null;
  };
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
 * « Gérer mon vol ». L'accès (PNR + nom) est porté par l'URL de la page de la réservation (`?n=…&l=…`), jamais par
 * le stockage du navigateur ; le dernier utilisé est gardé en mémoire pour préremplir le formulaire. Il est renvoyé
 * à chaque appel, le serveur ne faisant confiance à rien d'autre.
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
