import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Badge, BadgeTone, Button, Card, Modal, Spinner } from '@shared/ui';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { formatMoney } from '@core/money/money';
import { errorCode, IBookingAccess, ICancellationQuote, ManageBookingService, TBookingStatus } from './manage-booking.service';

const STATUS_TONES: Record<TBookingStatus, BadgeTone> = {
  confirmed: 'success',
  changed: 'warning',
  cancellation_pending: 'warning',
  cancelled: 'error',
};

/**
 * Page d'une réservation retrouvée (PNR + nom) : vols, voyageurs, montant payé et annulation en deux temps (devis
 * de la compagnie, puis confirmation). Rechargée, elle renvoie au formulaire : l'accès n'est gardé qu'en mémoire.
 */
@Component({
  selector: 'bip-booking-details',
  imports: [TranslocoDirective, DatePipe, RouterLink, Badge, Button, Card, Modal, Spinner],
  templateUrl: './booking-details.html',
})
export class BookingDetails implements OnInit {
  private readonly bookings = inject(ManageBookingService);
  private readonly router = inject(Router);
  private readonly access: IBookingAccess | null = this.bookings.access();
  private readonly locale = inject(TranslocoLocaleService);

  protected readonly view = this.bookings.current;
  protected readonly loadError = signal<string | null>(null);
  protected readonly statusTone = computed(() => STATUS_TONES[this.view()?.status ?? 'confirmed']);
  protected readonly cancellable = computed(() => {
    const view = this.view();
    return !!view && view.status !== 'cancelled' && !view.order.cancelled_at && !!view.order.available_actions?.includes('cancel');
  });

  /** Annulation : devis affiché dans la modale, puis confirmation. */
  protected readonly quote = signal<ICancellationQuote | null>(null);
  protected readonly cancelOpened = signal(false);
  protected readonly cancelBusy = signal(false);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly cancelled = signal(false);

  ngOnInit(): void {
    if (!this.access) {
      void this.router.navigate(['/flight', 'manage']);
      return;
    }
    // Arrivée depuis le formulaire : déjà chargée ; sinon relue (le cache serveur évite Duffel).
    if (this.view()?.order.booking_reference.toUpperCase() !== this.access.booking_reference.toUpperCase()) {
      this.reload();
    }
  }

  protected reload(): void {
    if (!this.access) {
      return;
    }
    this.loadError.set(null);
    this.bookings.lookup(this.access).subscribe({ error: (error: unknown) => this.loadError.set(`flight.manage.errors.${errorCode(error)}`) });
  }

  protected openCancellation(): void {
    if (!this.access) {
      return;
    }
    this.quote.set(null);
    this.cancelError.set(null);
    this.cancelOpened.set(true);
    this.cancelBusy.set(true);
    this.bookings.quoteCancellation(this.access).subscribe({
      next: (quote) => {
        this.cancelBusy.set(false);
        this.quote.set(quote);
      },
      error: (error: unknown) => {
        this.cancelBusy.set(false);
        this.cancelError.set(`flight.manage.errors.${errorCode(error)}`);
      },
    });
  }

  protected confirmCancellation(): void {
    const quote = this.quote();
    if (!this.access || !quote || this.cancelBusy()) {
      return;
    }
    this.cancelBusy.set(true);
    this.cancelError.set(null);
    this.bookings.confirmCancellation(this.access, quote.id).subscribe({
      next: () => {
        this.cancelBusy.set(false);
        this.cancelOpened.set(false);
        this.cancelled.set(true);
      },
      error: (error: unknown) => {
        this.cancelBusy.set(false);
        this.cancelError.set(`flight.manage.errors.${errorCode(error)}`);
      },
    });
  }

  /** Montant exact, dans sa devise : ni payé ni remboursé dans une autre devise que celle-ci. */
  protected money(amount: string, currency: string): string {
    return formatMoney(Number(amount), currency, this.locale.getLocale());
  }

  /** Autre réservation : l'accès gardé est oublié. */
  protected another(): void {
    this.bookings.forget();
    void this.router.navigate(['/flight', 'manage']);
  }
}
