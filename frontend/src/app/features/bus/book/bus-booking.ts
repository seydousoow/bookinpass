import { Component, computed, DestroyRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DOCUMENT, ViewportScroller } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroArrowRight } from '@ng-icons/heroicons/outline';
import { DatePipe } from '@core/i18n/date.pipe';
import { MoneyPipe } from '@core/money/money.pipe';
import { loadContact, saveContact } from '@core/session/contact';
import { IPaymentAuthorisation, PaymentForm } from '@core/payment/payment-form/payment-form';
import { Button } from '@shared/ui/button/button';
import { Spinner } from '@shared/ui/spinner/spinner';
import { IBusBooking, IBusDeparture } from '@shared/models/bus.model';
import { BusService, IBusHold } from '../bus.service';
import { BUS_LIMITS, toBusQueryParams } from '../bus-search-params';
import { BusTickets } from '../booking/bus-tickets';
import { BUS_STEPS, clearBusDraft, fitPassengers, IBusDraft, IBusTravellerDetails, loadBusDraft, saveBusDraft, TBusStep } from './bus-draft';
import { BusTravellerForm } from './bus-traveller-form';

/** Code d'erreur stable renvoyé par le serveur (`sold-out`, `hold-expired`…). */
const codeOf = (error: unknown): string | undefined =>
  error instanceof HttpErrorResponse ? (error.error as { code?: string } | null)?.code : undefined;

/**
 * Réservation d'un départ de car, en trois étapes : passagers, paiement, billets.
 *
 * Passer au paiement bloque les places (36 minutes) et ouvre la session Stripe ; le paiement autorisé, la réservation
 * est confirmée (places vendues, paiement encaissé, billets par e-mail). Le brouillon de l'onglet garde la saisie et la
 * demande en cours : un rechargement ou le retour d'un moyen à redirection reprennent la même réservation.
 */
@Component({
  selector: 'bip-bus-booking',
  imports: [TranslocoDirective, NgIcon, Button, Spinner, DatePipe, MoneyPipe, PaymentForm, BusTickets, BusTravellerForm],
  providers: [provideIcons({ heroArrowRight })],
  templateUrl: './bus-booking.html',
  host: { class: 'mx-auto block w-full max-w-3xl px-2 py-4' },
})
export class BusBooking {
  readonly route = input<string>();
  readonly departs = input<string>();
  readonly passengers = input<string>();
  readonly from = input<string>();
  readonly to = input<string>();
  /** Retour de PayDunya (Wave, Orange Money) : référence de la réservation, et `cancelled=1` si le client a abandonné. */
  readonly mobile = input<string>();
  readonly cancelled = input<string>();

  protected readonly steps = BUS_STEPS;
  protected readonly step = signal<TBusStep>('travellers');
  protected readonly stepIndex = computed(() => BUS_STEPS.indexOf(this.step()));
  protected readonly time: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' };
  protected readonly day: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' };

  /** Départ choisi : celui de l'URL, sinon celui du brouillon (retour de Stripe). */
  private readonly draft = signal<IBusDraft | null>(null);
  protected readonly choice = computed(() => {
    const draft = this.draft();
    const route = this.route() ?? draft?.route;
    const departs = this.departs() ?? draft?.departs;
    const from = this.from() ?? draft?.from;
    const to = this.to() ?? draft?.to;
    const count = Number(this.passengers() ?? draft?.passengers ?? 1);
    if (!route || !departs || !from || !to || !Number.isInteger(count) || count < 1 || count > BUS_LIMITS.maxPassengers) {
      return null;
    }
    return { route, departs, from, to, passengers: count };
  });

  private readonly api = inject(BusService);
  /** Le départ relu à l'ouverture : prix, gares, places encore disponibles. */
  private readonly lookup = rxResource({
    params: () => {
      const choice = this.choice();
      return choice ? { from: choice.from, to: choice.to, date: choice.departs.slice(0, 10), passengers: choice.passengers } : undefined;
    },
    stream: ({ params }) => this.api.departures(params),
  });
  protected readonly departure = computed((): IBusDeparture | null => {
    const choice = this.choice();
    return (this.lookup.hasValue() && this.lookup.value().find((d) => d.route_code === choice?.route && d.departs_at === choice?.departs)) || null;
  });
  protected readonly loading = this.lookup.isLoading;
  protected readonly failed = computed(() => this.lookup.status() === 'error');

  protected readonly details = signal<IBusTravellerDetails>({ passengers: [], email: '', phone: '' });
  private readonly travellerForm = viewChild(BusTravellerForm);
  protected readonly incomplete = signal(false);

  protected readonly hold = signal<IBusHold | null>(null);
  protected readonly holding = signal(false);
  /** Clé de traduction (sous `bus`) de l'échec du blocage des places. */
  protected readonly holdError = signal<string | null>(null);
  protected readonly confirming = signal(false);
  protected readonly confirmError = signal<string | null>(null);
  protected readonly booking = signal<IBusBooking | null>(null);
  /** Moyens de paiement du serveur : Wave / Orange Money seulement si PayDunya est configuré. */
  protected readonly methods = toSignal(this.api.paymentMethods().pipe(catchError(() => of({ card: true, mobile_money: false }))), {
    initialValue: { card: true, mobile_money: false },
  });
  /** Paiement mobile : redirection vers PayDunya, puis vérification au retour (en attente de validation sur le téléphone). */
  protected readonly mobileState = signal<'idle' | 'redirecting' | 'checking' | 'pending' | 'cancelled' | 'failed'>('idle');
  private readonly mobileReference = signal<string | null>(null);
  /** Réservation en cours : celle des places bloquées, ou celle payée sur PayDunya. */
  private readonly reference = computed(() => this.hold()?.reference ?? this.mobileReference());
  private mobileChecks = 0;

  private requestId: string = crypto.randomUUID();
  private readonly router = inject(Router);
  private readonly scroller = inject(ViewportScroller);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  constructor() {
    const draft = loadBusDraft();
    const contact = loadContact();
    effect(() => {
      const choice = this.choice();
      if (!choice) {
        return;
      }
      untracked(() => {
        // Même départ que le brouillon : sa saisie et sa demande reprennent ; sinon tout recommence.
        const same = draft && draft.route === choice.route && draft.departs === choice.departs && draft.passengers === choice.passengers;
        const details = same ? draft.details : { passengers: [], email: contact?.email ?? '', phone: contact?.phone ?? '' };
        this.details.set({ ...details, passengers: fitPassengers(details.passengers, choice.passengers) });
        if (same) {
          this.requestId = draft.requestId;
          if (draft.reference && this.mobile() === draft.reference) {
            // Retour de PayDunya : la réservation est vérifiée, pas de nouvelle session de carte.
            this.mobileReference.set(draft.reference);
            this.goTo('pay');
            this.checkMobile(this.cancelled() === '1');
          } else if (draft.reference) {
            this.goTo('pay');
            this.placeHold();
          }
        }
      });
    });
    this.draft.set(draft);
    effect(() => {
      const choice = this.choice();
      if (choice && !this.booking()) {
        saveBusDraft({ ...choice, requestId: this.requestId, details: this.details(), reference: this.reference() });
      }
    });
    effect(() => {
      const { email, phone } = this.details();
      if (email || phone) {
        saveContact({ email, phone });
      }
    });
    this.destroyRef.onDestroy(() => {
      if (this.booking()) {
        clearBusDraft();
      }
    });
  }

  protected toPayment(): void {
    if (!this.travellerForm()?.check()) {
      this.incomplete.set(true);
      return;
    }
    this.incomplete.set(false);
    this.goTo('pay');
    this.placeHold();
  }

  /** Retour aux passagers : une nouvelle demande sera faite (les noms des billets peuvent changer). */
  protected backToTravellers(): void {
    this.hold.set(null);
    this.mobileReference.set(null);
    this.mobileState.set('idle');
    this.holdError.set(null);
    this.requestId = crypto.randomUUID();
    this.goTo('travellers');
  }

  protected placeHold(): void {
    const choice = this.choice();
    if (!choice) {
      return;
    }
    const details = this.details();
    this.holding.set(true);
    this.holdError.set(null);
    this.api
      .hold({
        request_id: this.requestId,
        route_code: choice.route,
        departs_at: choice.departs,
        passengers: details.passengers.map((p) => ({ given_name: p.given_name.trim(), family_name: p.family_name.trim() })),
        email: details.email.trim(),
        phone: details.phone,
        return_path: '/bus/book',
      })
      .pipe(
        finalize(() => this.holding.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (hold) => this.hold.set(hold),
        error: (error: unknown) => {
          const code = codeOf(error);
          if (code === 'hold-expired') {
            // Places déjà rendues : nouvelle demande, de nouvelles places si le départ en a encore.
            this.requestId = crypto.randomUUID();
          }
          const known = ['sold-out', 'departure-closed', 'departure-not-found', 'route-not-found', 'hold-expired', 'payments-unavailable'];
          this.holdError.set(`pay.errors.${known.includes(code ?? '') ? code : 'failed'}`);
        },
      });
  }

  protected onAuthorised(authorisation: IPaymentAuthorisation): void {
    const hold = this.hold();
    if (!hold) {
      return;
    }
    this.confirming.set(true);
    this.confirmError.set(null);
    this.api
      .confirm(hold.reference, authorisation.checkoutSessionId)
      .pipe(
        finalize(() => this.confirming.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (booking) => {
          this.booking.set(booking);
          clearBusDraft();
          this.goTo('done');
        },
        error: (error: unknown) => this.confirmError.set(codeOf(error) === 'sold-out' ? 'pay.errors.sold-out-paid' : 'pay.errors.confirm-failed'),
      });
  }

  protected retryConfirmation(): void {
    const hold = this.hold();
    if (hold) {
      this.onAuthorised({ checkoutSessionId: hold.checkout.checkout_session_id });
    }
  }

  /** Wave / Orange Money : la session de carte est fermée côté serveur, puis direction la page PayDunya. */
  protected payMobile(): void {
    const reference = this.reference();
    if (!reference) {
      return;
    }
    this.mobileState.set('redirecting');
    this.confirmError.set(null);
    this.api
      .mobilePayment(reference, '/bus/book')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ url }) => this.document.location.assign(url),
        error: (error: unknown) => {
          this.mobileState.set('failed');
          const code = codeOf(error);
          this.confirmError.set(code === 'hold-expired' || code === 'already-paid' ? `pay.errors.${code}` : 'pay.errors.mobile-failed');
        },
      });
  }

  /**
   * Retour de PayDunya : le serveur relit la facture. En attente (validation sur le téléphone) : nouvelle lecture toutes
   * les 4 secondes, 3 minutes au plus ; abandon : le client peut réessayer ou payer par carte.
   */
  protected checkMobile(cancelled = false): void {
    const reference = this.reference();
    if (!reference) {
      return;
    }
    this.mobileState.set('checking');
    this.api
      .confirmMobile(reference)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (booking) => {
          if (booking.status === 'confirmed') {
            this.mobileState.set('idle');
            this.booking.set(booking);
            clearBusDraft();
            this.goTo('done');
          } else if (booking.status === 'released') {
            this.mobileState.set('failed');
            this.confirmError.set('pay.errors.hold-expired');
          } else if (cancelled) {
            this.mobileState.set('cancelled');
          } else if (++this.mobileChecks <= 45) {
            this.mobileState.set('pending');
            setTimeout(() => this.mobileState() === 'pending' && this.checkMobile(), 4000);
          } else {
            this.mobileState.set('pending');
          }
        },
        error: () => {
          this.mobileState.set('failed');
          this.confirmError.set('pay.errors.mobile-failed');
        },
      });
  }

  protected backToResults(): void {
    const choice = this.choice();
    clearBusDraft();
    void (choice
      ? this.router.navigate(['/bus', 'search'], {
          queryParams: toBusQueryParams({ from: choice.from, to: choice.to, date: choice.departs.slice(0, 10), passengers: choice.passengers }),
        })
      : this.router.navigate(['/bus']));
  }

  protected retry(): void {
    this.lookup.reload();
  }

  /** Frais de service Book In Pass de tous les passagers. */
  protected feesOf(departure: IBusDeparture): number {
    return Number(departure.fee) * (this.choice()?.passengers ?? 1);
  }

  private goTo(step: TBusStep): void {
    this.step.set(step);
    this.scroller.scrollToPosition([0, 0]);
  }
}
