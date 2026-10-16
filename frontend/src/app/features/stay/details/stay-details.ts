import { Component, computed, DestroyRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { DOCUMENT, ViewportScroller } from '@angular/common';
import { HttpErrorResponse, httpResource, HttpStatusCode } from '@angular/common/http';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, exhaustMap, filter, finalize, interval, Observable, tap } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { StaySearchStore } from '@core/search/stay-search.store';
import { deadline, inactivity, REVALIDATE_MS } from '@core/session/booking-timers';
import { IStayQuote, IStayRate, IStayResult, IStayResultResponse } from '@shared/models';
import { Button, Modal, Spinner } from '@shared/ui';
import { toStayQueryParams } from '../search/stay-search-params';
import { StayBookingService } from './stay-booking.service';
import { clearStayDraft, IStayGuestDetails, loadStayDraft, saveStayDraft, STAY_STEPS, TStayStep } from './stay-draft';
import { findRate, roomsByPrice } from './rate.util';
import { fitGuests, GuestForm, IGuestSlot } from './guest-form/guest-form';
import { StayStepper } from './stay-stepper';
import { StayHeader } from './stay-header/stay-header';
import { StayGallery } from './stay-gallery/stay-gallery';
import { StayOverview } from './stay-overview/stay-overview';
import { RoomCard } from './room-card/room-card';
import { StayReviews } from './stay-reviews/stay-reviews';
import { StayPriceSummary } from './price-summary/stay-price-summary';

/** Résultat ou tarif retiré chez Duffel (404, 422) ; une coupure réseau, elle, ne prouve rien. */
const isGone = (error: unknown): boolean =>
  error instanceof HttpErrorResponse && (error.status === HttpStatusCode.NotFound || error.status === HttpStatusCode.UnprocessableEntity);

const EMPTY_DETAILS: IStayGuestDetails = { guests: [], email: '', phone: '', requests: '' };

/**
 * Réservation d'un hébergement, en trois étapes : chambre et tarif, voyageurs, paiement.
 *
 * Mêmes principes que pour les vols : brouillon relu après un rechargement (une étape n'est rendue que si les
 * précédentes sont valides), tarifs relus toutes les 10 minutes, échéance du résultat surveillée, session
 * abandonnée après 15 minutes d'inactivité. Passer à l'étape « Voyageurs » vérifie le tarif (devis Duffel) :
 * prix définitif et disponibilité.
 */
@Component({
  selector: 'bip-stay-details',
  templateUrl: './stay-details.html',
  host: { class: 'container mx-auto block w-full max-w-6xl p-2' },
  imports: [
    TranslocoDirective,
    Button,
    Modal,
    Spinner,
    StayStepper,
    StayHeader,
    StayGallery,
    StayOverview,
    RoomCard,
    StayReviews,
    GuestForm,
    StayPriceSummary,
  ],
})
export class StayDetails {
  readonly resultId = input<string>('');
  protected readonly rooms = computed(() => roomsByPrice(this.result()?.accommodation.rooms ?? []));
  protected readonly steps = STAY_STEPS;
  /** Devis du tarif choisi : prix définitif. Remis à zéro à chaque changement de tarif. */
  protected readonly quote = signal<IStayQuote | null>(null);
  protected readonly quoting = signal(false);
  /** Vérification du tarif refusée : `gone`, il n'est plus disponible ; `error`, panne passagère. */
  protected readonly quoteFailed = signal<'gone' | 'error' | null>(null);
  /** Tarif du brouillon ou de la revalidation disparu : il faut en choisir un autre. */
  protected readonly rateLost = signal(false);
  /** « Continuer » sans tarif choisi, ou voyageurs incomplets. */
  protected readonly missingRate = signal(false);
  protected readonly incomplete = signal(false);
  protected readonly details = signal<IStayGuestDetails>(EMPTY_DETAILS);
  /** Un formulaire par voyageur de la recherche : adultes puis enfants, avec leur âge. */
  protected readonly slots = computed((): IGuestSlot[] => {
    const seen = { adult: 0, child: 0 };
    return (this.result()?.guests ?? []).map(({ type, age }) => ({ type, index: ++seen[type], age: type === 'child' ? (age ?? null) : null }));
  });
  /** Inactivité prolongée : la page se fige derrière une modale qui renvoie à la recherche. */
  protected readonly idle = signal(false);
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly api = inject(StayBookingService);
  private readonly _result = httpResource<IStayResultResponse>(() =>
    this.resultId() ? `${this.apiBaseUrl}/stays/results/${encodeURIComponent(this.resultId())}/rates` : undefined,
  );
  protected readonly loading = computed(() => this._result.isLoading() && !this._result.hasValue());
  protected readonly result = computed((): IStayResult | undefined => (this._result.hasValue() ? this._result.value().data : undefined));
  /** Panne passagère au chargement : on propose de réessayer. */
  protected readonly failed = computed(() => this._result.status() === 'error' && !isGone(this._result.error()));
  /** Résultat expiré : au chargement, à la revalidation ou à son échéance. */
  private readonly expired = signal(false);
  protected readonly unavailable = computed(() => this.expired() || (this._result.status() === 'error' && isGone(this._result.error())));
  private readonly step = signal<TStayStep>('room');
  protected readonly activeStep = this.step.asReadonly();
  protected readonly stepIndex = computed(() => STAY_STEPS.indexOf(this.step()));
  private readonly rateId = signal<string | null>(null);
  protected readonly selectedRateId = this.rateId.asReadonly();
  protected readonly choice = computed(() => findRate(this.result(), this.rateId()));
  private readonly guestForm = viewChild(GuestForm);
  /** Brouillon relu (ou absent) : avant, rien n'est enregistré, pour ne pas écraser celui d'avant le rechargement. */
  private readonly restored = signal(false);

  private readonly router = inject(Router);
  private readonly scroller = inject(ViewportScroller);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchStore = inject(StaySearchStore);

  constructor() {
    effect(() => {
      const result = this.result();
      if (result && !this.restored()) {
        untracked(() => this.restore(result));
      }
    });
    effect(() => {
      if (this.restored() && !this.idle()) {
        saveStayDraft(this.resultId(), { step: this.step(), rateId: this.rateId(), details: this.details() });
      }
    });

    interval(REVALIDATE_MS)
      .pipe(
        filter(() => !!this.result() && !this.idle() && !this.unavailable()),
        exhaustMap(() => this.revalidate()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();

    // Échéance du résultat passée : Duffel refusera le devis, on le dit sans attendre la revalidation.
    deadline(computed(() => this.result()?.expires_at))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.expired.set(true));
    inactivity(inject(DOCUMENT))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.expireSession());
  }

  protected chooseRate(rate: IStayRate): void {
    if (rate.id === this.rateId()) {
      return;
    }
    this.rateId.set(rate.id);
    this.quote.set(null);
    this.quoteFailed.set(null);
    this.rateLost.set(false);
    this.missingRate.set(false);
  }

  protected next(): void {
    switch (this.step()) {
      case 'room':
        if (!this.choice()) {
          this.missingRate.set(true);
          return;
        }
        if (this.quote()) {
          this.goTo('guests');
          return;
        }
        this.verifyRate('guests');
        return;
      case 'guests':
        if (!this.guestForm()?.check()) {
          this.incomplete.set(true);
          return;
        }
        this.incomplete.set(false);
        this.goTo('pay');
        return;
    }
  }

  protected previous(): void {
    const preceding = STAY_STEPS[this.stepIndex() - 1];
    if (preceding) {
      this.goTo(preceding);
    }
  }

  protected retry(): void {
    this._result.reload();
  }

  /** Retour aux résultats de la dernière recherche ; sans recherche mémorisée, au formulaire. */
  protected returnToSearch(): void {
    const search = this.searchStore.lastSearch();
    void (search ? this.router.navigate(['/stays', 'search'], { queryParams: toStayQueryParams(search) }) : this.router.navigate(['/stays']));
  }

  /** Devis Duffel du tarif choisi, puis passage à `then`. Tarif disparu : retour au choix, tarifs relus. */
  private verifyRate(then: TStayStep): void {
    const rateId = this.rateId();
    if (!rateId) {
      return;
    }
    this.quoting.set(true);
    this.quoteFailed.set(null);
    this.api
      .createQuote(rateId)
      .pipe(
        finalize(() => this.quoting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (quote) => {
          this.quote.set(quote);
          this.goTo(then);
        },
        error: (error: unknown) => {
          if (isGone(error)) {
            this.quoteFailed.set('gone');
            this.rateId.set(null);
            this.goTo('room');
            this.revalidate().subscribe();
          } else {
            this.quoteFailed.set('error');
          }
        },
      });
  }

  /** Relit les tarifs ; le tarif choisi a disparu, on revient au choix de la chambre. */
  private revalidate(): Observable<unknown> {
    return this.api.getRates(this.resultId()).pipe(
      tap((result) => {
        this._result.set({ data: result });
        if (this.rateId() && !findRate(result, this.rateId())) {
          this.loseRate();
        }
      }),
      catchError((error: unknown) => {
        if (isGone(error)) {
          this.expired.set(true);
        }
        return EMPTY;
      }),
    );
  }

  private loseRate(): void {
    this.rateId.set(null);
    this.quote.set(null);
    this.rateLost.set(true);
    this.goTo('room');
  }

  private expireSession(): void {
    this.idle.set(true);
    clearStayDraft(this.resultId());
  }

  /**
   * Reprise après rechargement : saisies, tarif choisi puis étape. Au-delà du choix de la chambre, le tarif
   * est revérifié (le prix a pu changer) ; une étape « Paiement » avec des voyageurs incomplets revient à leur saisie.
   */
  private restore(result: IStayResult): void {
    const draft = loadStayDraft(this.resultId());
    const details = draft?.details ?? EMPTY_DETAILS;
    this.details.set({ ...details, guests: fitGuests(details.guests, this.slots().length) });
    if (!draft) {
      this.restored.set(true);
      return;
    }
    const rate = findRate(result, draft.rateId);
    if (draft.rateId && !rate) {
      this.rateLost.set(true);
    }
    this.rateId.set(rate ? draft.rateId : null);
    this.restored.set(true);
    if (!rate || draft.step === 'room') {
      return;
    }
    const complete = this.details().guests.every((g) => g.given_name && g.family_name) && !!this.details().email && !!this.details().phone;
    if (draft.step === 'pay' && !complete) {
      this.incomplete.set(true);
    }
    this.verifyRate(draft.step === 'pay' && complete ? 'pay' : 'guests');
  }

  private goTo(step: TStayStep): void {
    this.step.set(step);
    this.scroller.scrollToPosition([0, 0]);
  }
}
