import { Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { DOCUMENT, ViewportScroller } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, concatMap, EMPTY, exhaustMap, filter, finalize, from, interval, last, Observable, Subject, tap } from 'rxjs';
import { HttpErrorResponse, httpResource, HttpStatusCode } from '@angular/common/http';
import { email, form, FormField, required, validate } from '@angular/forms/signals';
import { TranslocoDirective } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheck, heroChevronDown, heroInformationCircle, heroUser } from '@ng-icons/heroicons/outline';
import { Button, Modal, Spinner, TextInput } from '@shared/ui';
import { isValidPhone, PhoneInput } from '@shared/ui/phone-input/phone-input';
import {
  IFlightOfferDetails,
  IFlightOfferResponse,
  IIntendedService,
  IOfferSlice,
  IPassengerUpdate,
  ISeatChoice,
  IUpsellOffersResponse,
  TBaggageType,
  TPassengerType,
} from '@shared/models';
import { ITravelerInfo, TravelerForm } from './traveler-form/traveler-form';
import { Router } from '@angular/router';
import { BipOfferPriceDetails } from '@features/flight/details/price-details/offer-price-details';
import { FlightOfferService, IFlightOrder, IOrderPassenger } from './flight-offer.service';
import { OfferItinerary } from './offer-itinerary/offer-itinerary';
import { FareOptions } from './fare-options/fare-options';
import { clearDraft, IContact, loadDraft, saveDraft, STEPS, TStep } from './checkout-draft';
import { SearchStore } from '@core/search/search.store';
import { loadContact, saveContact } from '@core/session/contact';
import { deadline, inactivity, REVALIDATE_MS } from '@core/session/booking-timers';
import { FlightDetailsLoader } from './flight-details-loader';
import { ISeatTraveler, SeatSelectionComponent } from '@features/flight/details/seat-selection/seat-selection';
import { extraBagGroups, extraBagLines, IExtraBagGroup, TBagSelection, toOrderServices } from './extra-bags/extra-bags.util';
import { ExtraBags, IExtrasTraveler } from './extra-bags/extra-bags';
import { IBagLine, IncludedBags } from './included-bags/included-bags';
import { IPaymentAuthorisation, PaymentForm } from '@core/payment/payment-form/payment-form';
import { ICheckoutSession, PaymentService } from '@core/payment/payment.service';
import { formatMoney } from '@core/money/money';

interface ITravelerCard {
  readonly id: string;
  /** Rang dans `offer.passengers` : stable d'une offre à l'autre, contrairement à l'identifiant Duffel. */
  readonly position: number;
  readonly type: TPassengerType;
  /** Rang du voyageur au sein de son type : « Adulte n° 1 », « Enfant n° 1 »… */
  readonly index: number;
  /** Nom et fidélité déjà enregistrés chez Duffel (`null` tant que le voyageur n'est pas renseigné). */
  readonly saved: Pick<ITravelerInfo, 'given_name' | 'family_name' | 'loyalty_programme_accounts'> | null;
  /** Bagages payants proposés, segment par segment (affichés avant les bagages inclus). */
  readonly extraBags: readonly IExtraBagGroup[];
  /** Bagages identiques sur tous les vols. */
  readonly everyFlight: readonly IBagLine[];
  /** Bagages propres à un vol — uniquement les écarts par rapport à `everyFlight`. */
  readonly perSlice: readonly { readonly destination: string; readonly bags: readonly IBagLine[] }[];
}

const BAGGAGE_TYPES: readonly TBaggageType[] = ['checked', 'carry_on'];


/** Offre retirée ou expirée chez Duffel ; une coupure réseau, elle, ne prouve rien. */
const isGone = (error: unknown): boolean =>
  error instanceof HttpErrorResponse && (error.status === HttpStatusCode.NotFound || error.status === HttpStatusCode.UnprocessableEntity);

@Component({
  selector: 'bip-flight-offer',
  templateUrl: './flight-offer.html',
  host: { class: 'container min-h-xl w-full max-w-6xl mx-auto p-2 block' },
  imports: [
    Spinner,
    Modal,
    TranslocoDirective,
    Button,
    NgIcon,
    TextInput,
    PhoneInput,
    FormField,
    TravelerForm,
    BipOfferPriceDetails,
    OfferItinerary,
    FareOptions,
    SeatSelectionComponent,
    FlightDetailsLoader,
    PaymentForm,
    ExtraBags,
    IncludedBags,
  ],
  providers: [provideIcons({ heroUser, heroChevronDown, heroCheck, heroInformationCircle })],
})
export class FlightOffer {
  readonly offerId = input<string>('', { transform: (v) => atob(v as string) });
  protected readonly firstSlice = computed(() => this.offer()?.slices[0]);
  protected readonly isRoundTrip = computed(() => (this.offer()?.slices.length ?? 0) > 1);

  protected readonly travelers = computed((): ITravelerCard[] => {
    const offer = this.offer();
    if (!offer) {
      return [];
    }
    const seen = new Map<TPassengerType, number>();
    return offer.passengers.map(({ id, type, given_name, family_name, loyalty_programme_accounts }, position) => {
      const key = type ?? 'adult';
      const index = (seen.get(key) ?? 0) + 1;
      seen.set(key, index);

      const slices = offer.slices.map((slice) => ({ destination: slice.destination.city_name, bags: this.bagsFor(slice, id) }));
      const everyFlight = BAGGAGE_TYPES.map((t) => ({ type: t, quantity: slices[0]?.bags.get(t) ?? 0 })).filter(
        ({ type: t, quantity }) => quantity > 0 && slices.every((s) => s.bags.get(t) === quantity),
      );
      const common = new Set(everyFlight.map((b) => b.type));
      const perSlice = slices
        .map(({ destination, bags }) => ({
          destination,
          bags: BAGGAGE_TYPES.filter((t) => !common.has(t)).map((t) => ({ type: t, quantity: bags.get(t) ?? 0 })),
        }))
        .filter((s) => s.bags.length > 0);

      const saved = given_name && family_name ? { given_name, family_name, loyalty_programme_accounts: [...loyalty_programme_accounts] } : null;
      return { id, position, type: key, index, saved, extraBags: extraBagGroups(offer, id), everyFlight, perSlice };
    });
  });
  /** Date du dernier vol : les compagnies vérifient l'âge des voyageurs à cette date. */
  protected readonly lastFlightDate = computed(() => {
    const segments = this.offer()?.slices.at(-1)?.segments ?? [];
    return segments.at(-1)?.departing_at ?? '';
  });
  /** Identités saisies, par rang de passager : elles suivent le voyageur si l'on change de tarif. */
  protected readonly travelerInfo = signal<Readonly<Record<number, ITravelerInfo>>>({});
  /**
   * Enregistrements chez Duffel en cours ou échoués, par rang de passager. Ils partent en arrière-plan,
   * l'un après l'autre : chaque réponse est l'offre complète, la dernière arrivée doit être la plus récente.
   */
  protected readonly saveState = signal<Readonly<Record<number, 'saving' | 'failed'>>>({});
  protected readonly saving = computed(() => Object.values(this.saveState()).includes('saving'));
  /** Sièges retenus à l'étape « Sélectionnez votre siège », tous vols confondus. */
  protected readonly seats = signal<readonly ISeatChoice[]>([]);
  /**
   * Bagages supplémentaires retenus (service Duffel → quantité). À la création de la commande, `toOrderServices`
   * en fait le champ `services` de `POST /air/orders`, et leur prix s'ajoute au montant du paiement.
   */
  protected readonly bags = signal<TBagSelection>({});
  /** Session Checkout Stripe de l'étape « Vérifiez et payez », au montant calculé par le backend. */
  protected readonly checkoutSession = signal<ICheckoutSession | null>(null);
  /** Clé de traduction de l'échec de préparation du paiement. */
  protected readonly checkoutSessionError = signal<string | null>(null);
  /**
   * Paiement autorisé (pas encore capturé) : la commande Duffel sera créée puis le paiement capturé (création de
   * commande pas encore branchée).
   */
  protected readonly paymentAuthorisation = signal<IPaymentAuthorisation | null>(null);
  /** Réservation en cours chez la compagnie, une fois le paiement autorisé. */
  protected readonly booking = signal(false);
  /** Clé de traduction de l'échec de la réservation ; `retry` : la même demande peut être renvoyée. */
  protected readonly bookingError = signal<{ key: string; retry: boolean } | null>(null);
  /** Réservation confirmée : la page n'affiche plus que la confirmation. */
  protected readonly order = signal<IFlightOrder | null>(null);
  /** Tarification finale en cours à l'étape « Vérifiez et payez », ou en échec. */
  protected readonly pricing = signal(false);
  protected readonly pricingFailed = signal(false);
  /** Choix encore valables sur l'offre courante : un autre tarif a d'autres services, les anciens sont ignorés. */
  protected readonly bagLines = computed(() => {
    const offer = this.offer();
    return offer ? extraBagLines(offer, this.bags()) : [];
  });
  /** Services prévus (bagages, puis un par siège) : envoyés à la tarification finale, puis à la commande. */
  protected readonly intendedServices = computed((): IIntendedService[] => [
    ...toOrderServices(this.bagLines()),
    ...this.seats().map((seat) => ({ id: seat.serviceId, quantity: 1 })),
  ]);
  /** Voyageurs pour le plan de cabine : identifiant Duffel et nom connu. */
  protected readonly seatTravelers = computed((): ISeatTraveler[] =>
    this.travelers().map((traveler) => {
      const known = this.knownTraveler(traveler);
      return { id: traveler.id, type: traveler.type, index: traveler.index, name: known ? `${known.given_name} ${known.family_name}` : null };
    }),
  );
  /** Voyageurs de l'étape « Options supplémentaires » : nom connu et bagages, payants puis inclus. */
  protected readonly extrasTravelers = computed((): IExtrasTraveler[] => {
    const names = new Map(this.seatTravelers().map((t) => [t.id, t.name]));
    return this.travelers().map(({ id, type, index, extraBags, everyFlight, perSlice }) => ({
      id,
      type,
      index,
      name: names.get(id) ?? null,
      extraBags,
      everyFlight,
      perSlice,
    }));
  });
  /** Voyageur dont la modale de saisie est ouverte. */
  protected readonly editing = signal<ITravelerCard | null>(null);
  /** Offre en cours de bascule, et échec éventuel de la dernière. */
  protected readonly pendingFare = signal<string | null>(null);
  protected readonly fareFailed = signal(false);
  /** Passage à l'étape suivante refusé faute de saisie ; le message suit ensuite la saisie (il disparaît une fois complète). */
  private readonly attempted = signal(false);
  protected readonly incomplete = computed(() => this.attempted() && !this.infoFilled());
  /** Inactivité prolongée : la page se fige derrière une modale qui renvoie à la recherche. */
  protected readonly idle = signal(false);
  private readonly paymentService = inject(PaymentService);
  /** Rang du paiement (voir `ICheckoutDraft.paymentAttempt`). */
  private readonly paymentAttempt = signal(0);
  private readonly document = inject(DOCUMENT);
  /** Offre et services de la session courante : une nouvelle n'est demandée que s'ils changent. */
  private checkoutSessionKey = '';
  /** Identifiant de l'offre courante : la tarification ne se relance que s'il change (autre tarif). */
  private readonly currentOfferId = computed(() => this.offer()?.id);
  /** Coordonnées : celles de la réservation précédente dans l'onglet, s'il y en a (`bip-phone-input` rend du E.164). */
  private readonly contact = signal<IContact>(loadContact() ?? { email: '', phone: '' });
  // Nom racine `contact` : champs `contact.email` / `contact.phone`, reconnus par la saisie automatique des navigateurs.
  protected readonly contactForm = form(
    this.contact,
    (path) => {
      required(path.email, { message: 'common.required-field' });
      email(path.email, { message: 'flight.offer.contact.email-invalid' });
      // Duffel l'exige pour chaque voyageur, au format international.
      required(path.phone, { message: 'common.required-field' });
      validate(path.phone, ({ value }) => (!value() || isValidPhone(value()) ? null : { kind: 'phone', message: 'flight.offer.contact.phone-invalid' }));
    },
    { name: 'contact' },
  );
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly api = inject(FlightOfferService);
  private readonly scroller = inject(ViewportScroller);
  private readonly destroyRef = inject(DestroyRef);
  /** Offre choisie en recherche. Sa valeur est remplacée par les réponses du backend (voyageurs renseignés…). */
  private readonly _offer = httpResource<IFlightOfferResponse>(() => `${this.apiBaseUrl}/flights/offers/${encodeURIComponent(this.offerId())}`);
  readonly isLoading = computed(() => this._offer.isLoading() && !this._offer.hasValue());
  /** Offre de départ ; `undefined` tant qu'elle charge ou si elle est en erreur (`value()` lèverait une exception). */
  private readonly baseOffer = computed(() => (this._offer.hasValue() ? this._offer.value().data : undefined));
  /** Offre retirée chez Duffel, au chargement ou lors d'une revalidation. */
  private readonly expired = signal(false);
  readonly error = computed(() => this._offer.status() === 'error' || this.expired());
  /** Échéance de l'offre affichée ; une montée en gamme a la sienne. */
  private readonly expiresAt = computed(() => this.offer()?.expires_at);
  /** Montées en gamme de l'offre choisie en recherche ; chargées d'emblée pour savoir s'il faut l'étape « Type de billet ». */
  private readonly upsells = httpResource<IUpsellOffersResponse>(() =>
    this.offerId() ? `${this.apiBaseUrl}/flights/offers/${encodeURIComponent(this.offerId())}/upsells` : undefined,
  );
  protected readonly upsellsLoading = this.upsells.isLoading;
  protected readonly fareOptions = computed((): IFlightOfferDetails[] => {
    const base = this.baseOffer();
    return base ? [base, ...(this.upsells.value()?.data ?? [])] : [];
  });
  /** Montée en gamme retenue ; `null` : on garde l'offre de départ. */
  private readonly upgrade = signal<IFlightOfferDetails | null>(null);
  /** Offre en cours de réservation : celle de départ ou la montée en gamme choisie. */
  readonly offer = computed(() => this.upgrade() ?? this.baseOffer());
  /** Sans montée en gamme (ou si Duffel ne répond pas), l'étape « Type de billet » disparaît. */
  private readonly noUpsell = computed(() => this.upsells.status() === 'error' || (this.upsells.hasValue() && !this.upsells.value().data.length));
  protected readonly steps = computed(() => STEPS.filter((step) => step !== 'ticket' || !this.noUpsell()));
  private readonly step = signal<TStep>('info');
  /** Étape affichée : « Type de billet » demandée avant la fin du chargement, puis vide, cède la place à la suivante. */
  protected readonly activeStep = computed((): TStep => (this.step() === 'ticket' && this.noUpsell() ? 'extras' : this.step()));
  protected readonly stepIndex = computed(() => this.steps().indexOf(this.activeStep()));
  private readonly localeService = inject(TranslocoLocaleService);
  private readonly locale = toSignal(this.localeService.localeChanges$, { initialValue: this.localeService.getLocale() });
  /** Montant réellement débité, dans sa devise (pas de conversion d'affichage). */
  protected readonly chargedAmount = computed(() => {
    const order = this.order();
    return order ? formatMoney(Number(order.amount), order.currency, this.locale()) : '';
  });
  /** « sam. 31 oct. - sam. 7 nov. » — `translocoDate` impose un `dateStyle` incompatible avec `weekday`. */
  protected readonly travelDates = computed(() => {
    const slices = this.offer()?.slices ?? [];
    const format = new Intl.DateTimeFormat(this.locale(), { weekday: 'short', day: 'numeric', month: 'short' });
    return slices
      .filter((_, i) => i === 0 || i === slices.length - 1)
      .map((slice) => slice.segments[0]?.departing_at)
      .filter((date): date is string => !!date)
      .map((date) => format.format(new Date(date)))
      .join(' - ');
  });

  /** Dates de naissance formatées, par passager. `born_on` est une date calendaire : lue en UTC pour ne pas glisser d'un jour. */
  protected readonly birthDates = computed(() => {
    const format = new Intl.DateTimeFormat(this.locale(), { dateStyle: 'long', timeZone: 'UTC' });
    return Object.fromEntries(
      Object.entries(this.travelerInfo())
        .filter(([, info]) => !!info.born_on)
        .map(([id, info]) => [id, format.format(new Date(`${info.born_on}T00:00:00Z`))]),
    );
  });

  private readonly router = inject(Router);
  private readonly saves = new Subject<number>();

  /** Brouillon relu (ou absent) : avant, rien n'est enregistré, pour ne pas écraser celui d'avant le rechargement. */
  private readonly restored = signal(false);
  private readonly searchStore = inject(SearchStore);

  constructor() {
    // Sortie de la page de l'offre (navigation dans l'application) : la réservation en cours est oubliée.
    // Un rechargement ou la redirection Stripe ne détruisent pas le composant : le brouillon reste pour la reprise.
    this.destroyRef.onDestroy(() => clearDraft(this.offerId()));
    effect(() => {
      const base = this.baseOffer();
      if (base && !this.restored()) {
        untracked(() => this.restore(base));
      }
    });
    effect(() => {
      // Paiement autorisé ou réservation faite : plus de brouillon (un paiement libéré relance l'enregistrement).
      if (this.restored() && !this.idle() && !this.order() && !this.paymentAuthorisation()) {
        saveDraft(this.offerId(), {
          step: this.activeStep(),
          fareOfferId: this.upgrade()?.id ?? null,
          travelers: this.travelerInfo(),
          contact: this.contact(),
          seats: this.seats(),
          bags: this.bags(),
          paymentAttempt: this.paymentAttempt(),
        });
      }
    });

    // Coordonnées reprises pour les réservations suivantes de l'onglet.
    effect(() => {
      if (this.restored()) {
        saveContact(this.contact());
      }
    });

    // Étape « Vérifiez et payez » : l'offre est tarifée chez Duffel avec les services prévus, et la remplace.
    effect(() => {
      if (this.activeStep() === 'pay' && this.currentOfferId()) {
        untracked(() => this.priceOffer().pipe(takeUntilDestroyed(this.destroyRef)).subscribe());
      }
    });

    // Revalidation périodique : l'offre relue remplace l'actuelle (prix à jour) ; disparue, on le signale.
    interval(REVALIDATE_MS)
      .pipe(
        filter(() => !!this.offer() && !this.idle() && !this.error() && !this.order() && !this.booking()),
        exhaustMap(() => this.revalidate()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();

    // `expires_at` passé, Duffel refusera la commande (`offer_expired`) : on le dit sans attendre la revalidation.
    deadline(this.expiresAt)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => !this.order() && !this.booking() && this.expired.set(true));
    inactivity(this.document)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => !this.order() && !this.booking() && this.expireSession());

    this.saves
      .pipe(
        concatMap((position) => this.persistTraveler(position)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  /** Retour aux résultats de la dernière recherche ; sans recherche mémorisée, à l'accueil. */
  protected returnHomePage(): void {
    const search = this.searchStore.lastSearch();
    if (!search) {
      void this.router.navigate(['/flight']);
      return;
    }
    const { tripType, origin, destination, dates, passengers, cabin } = search;
    void this.router.navigate(['/flight', 'search'], {
      queryParams: {
        t: tripType,
        o: origin,
        d: destination,
        from: dates.start,
        to: tripType === 'round' ? dates.end : undefined,
        adt: passengers.adults,
        chd: passengers.children || undefined,
        inf: passengers.infants || undefined,
        cabin,
      },
    });
  }

  /** Le voyageur s'affiche aussitôt ; son enregistrement chez Duffel suit en arrière-plan. */
  protected saveTraveler(traveler: ITravelerCard, info: ITravelerInfo): void {
    this.travelerInfo.update((all) => ({ ...all, [traveler.position]: info }));
    this.retrySave(traveler.position);
  }

  protected retrySave(position: number): void {
    this.saveState.update((all) => ({ ...all, [position]: 'saving' }));
    this.saves.next(position);
  }

  /** Infos à afficher et à préremplir : la saisie de cette page, sinon le nom déjà enregistré chez Duffel. */
  protected knownTraveler(traveler: ITravelerCard): Partial<ITravelerInfo> | undefined {
    const info: ITravelerInfo | undefined = this.travelerInfo()[traveler.position];
    return info ?? traveler.saved ?? undefined;
  }

  protected next(): void {
    if (this.saving()) {
      return;
    }
    if (this.activeStep() === 'info' && !this.infoComplete()) {
      this.attempted.set(true);
      this.contactForm().markAsTouched();
      return;
    }
    this.attempted.set(false);
    const steps = this.steps();
    const following = steps[steps.indexOf(this.activeStep()) + 1];
    if (following) {
      this.goTo(following);
    }
  }

  protected previous(): void {
    const steps = this.steps();
    const preceding = steps[steps.indexOf(this.activeStep()) - 1];
    if (preceding) {
      this.goTo(preceding);
    }
  }

  /**
   * Bascule vers un autre tarif. Les voyageurs déjà saisis y sont reportés (nouveaux identifiants Duffel) ;
   * la réponse du backend, offre complète avec ses services, devient l'offre courante.
   */
  protected selectFare(target: IFlightOfferDetails): void {
    this.pendingFare.set(target.id);
    this.fareFailed.set(false);
    const travelers = Object.entries(this.travelerInfo());
    const switched: Observable<IFlightOfferDetails> = travelers.length
      ? from(travelers).pipe(
          concatMap(([position, info]) => this.api.updatePassenger(target.id, target.passengers[Number(position)].id, toPassengerUpdate(info))),
          last(),
        )
      : this.api.getOffer(target.id);
    switched
      .pipe(
        finalize(() => this.pendingFare.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (offer) => {
          this.replaceOffer(offer);
          this.saveState.set({});
        },
        error: () => this.fareFailed.set(true),
      });
  }

  protected getStepClass(i: number): string {
    if (i === this.stepIndex()) {
      return 'bg-brand-primary border-brand-primary text-onbrand';
    }
    if (i < this.stepIndex()) {
      return 'border-brand-primary text-brand-primary';
    }
    return 'border-ink';
  }

  /** Tarifie l'offre courante avec les services prévus ; l'offre rendue remplace l'actuelle. */
  protected retryPricing(): void {
    this.priceOffer().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  /** Paiement autorisé : la réservation part aussitôt ; le paiement n'est encaissé que si elle aboutit. */
  protected onAuthorised(authorisation: IPaymentAuthorisation): void {
    this.paymentAuthorisation.set(authorisation);
    clearDraft(this.offerId());
    this.book();
  }

  protected book(): void {
    const offer = this.offer();
    const authorisation = this.paymentAuthorisation();
    if (!offer || !authorisation || this.booking()) {
      return;
    }
    const passengers = offer.passengers.map(({ id }, position): IOrderPassenger => {
      const info = this.travelerInfo()[position]!;
      return {
        id,
        title: info.title,
        gender: info.gender,
        given_name: info.given_name,
        family_name: info.family_name,
        born_on: info.born_on,
        identity_documents: info.identity_documents,
      };
    });
    this.booking.set(true);
    this.bookingError.set(null);
    this.api
      .createOrder({
        offer_id: offer.id,
        checkout_session_id: authorisation.checkoutSessionId,
        email: this.contact().email.trim(),
        phone_number: this.contact().phone,
        passengers,
      })
      .pipe(
        finalize(() => this.booking.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => {
          this.order.set(order);
          clearDraft(this.offerId());
          this.scroller.scrollToPosition([0, 0]);
        },
        error: (error: unknown) => this.onBookingError(error),
      });
  }

  /** Voyageurs refusés par la compagnie : retour à « Vos informations ». */
  protected editTravelers(): void {
    this.bookingError.set(null);
    this.goTo('info');
  }

  protected retryPayment(): void {
    this.preparePayment().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  /** Voyageur saisi — passeport compris quand l'offre l'exige (un autre tarif peut l'exiger après coup). */
  protected travelerFilled(position: number): boolean {
    const info: ITravelerInfo | undefined = this.travelerInfo()[position];
    return !!info && (!this.offer()?.passenger_identity_documents_required || !!info.identity_documents?.length);
  }

  /** Relit l'offre chez Duffel ; disparue, la modale « vol indisponible » prend le relais. */
  private revalidate(): Observable<unknown> {
    // À l'étape de paiement, l'offre courante est tarifée : on la retarife plutôt que de la relire nue.
    if (this.activeStep() === 'pay') {
      return this.priceOffer();
    }
    return this.api.getOffer(this.offer()!.id).pipe(
      tap((offer) => this.replaceOffer(offer)),
      catchError((error: unknown) => {
        if (isGone(error)) {
          this.expired.set(true);
        }
        return EMPTY;
      }),
    );
  }

  private priceOffer(): Observable<unknown> {
    const offer = this.offer();
    if (!offer) {
      return EMPTY;
    }
    this.pricing.set(true);
    this.pricingFailed.set(false);
    return this.api.price(offer.id, this.intendedServices()).pipe(
      tap((priced) => this.replaceOffer(priced)),
      // Prix confirmé : session Checkout Stripe (le montant est recalculé côté serveur).
      concatMap(() => this.preparePayment()),
      catchError((error: unknown) => {
        if (isGone(error)) {
          this.expired.set(true);
        } else {
          this.pricingFailed.set(true);
        }
        return EMPTY;
      }),
      finalize(() => this.pricing.set(false)),
    );
  }

  private preparePayment(): Observable<unknown> {
    const offerId = this.offer()!.id;
    const services = this.intendedServices();
    const key = JSON.stringify([offerId, services, this.paymentAttempt()]);
    if (key === this.checkoutSessionKey && this.checkoutSession()) {
      return EMPTY;
    }
    this.checkoutSessionError.set(null);
    return this.paymentService.flightCheckout(offerId, services, this.document.location.pathname, this.paymentAttempt()).pipe(
      tap((session) => {
        this.checkoutSessionKey = key;
        this.checkoutSession.set(session);
      }),
      catchError((error: unknown) => {
        const code = error instanceof HttpErrorResponse ? (error.error as { code?: string } | null)?.code : undefined;
        this.checkoutSessionError.set(
          code === 'payments-unavailable' || code === 'service-unavailable' ? `payment.errors.${code}` : 'payment.errors.intent',
        );
        return EMPTY;
      }),
    );
  }

  /**
   * Hors coupure réseau ou panne passagère (la demande, rejouable, est alors proposée de nouveau), le serveur a libéré
   * le paiement : rien n'est débité, un nouveau paiement est ouvert.
   */
  private onBookingError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const code = error instanceof HttpErrorResponse ? (error.error as { code?: string } | null)?.code : undefined;
    if (code === 'booking-in-progress') {
      return;
    }
    if (
      status === 0 ||
      status === HttpStatusCode.BadGateway ||
      status === HttpStatusCode.ServiceUnavailable ||
      status === HttpStatusCode.GatewayTimeout
    ) {
      this.bookingError.set({ key: 'flight.offer.booking.errors.failed', retry: true });
      return;
    }
    this.releasePayment();
    if (isGone(error) || code === 'offer-unavailable' || code === 'offer-not-found') {
      this.expired.set(true);
    } else if (status === HttpStatusCode.BadRequest) {
      // Voyageurs refusés par la compagnie : à corriger avant de payer de nouveau.
      this.bookingError.set({ key: 'flight.offer.booking.errors.rejected', retry: false });
    } else {
      this.bookingError.set({
        key: code === 'price-changed' ? 'flight.offer.booking.errors.price-changed' : 'flight.offer.booking.errors.released',
        retry: false,
      });
      this.priceOffer().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    }
  }

  /** Paiement libéré par le serveur : le suivant ouvrira une nouvelle session Stripe. */
  private releasePayment(): void {
    this.paymentAttempt.update((n) => n + 1);
    this.paymentAuthorisation.set(null);
    this.checkoutSession.set(null);
    this.checkoutSessionKey = '';
  }

  /** Session abandonnée : le brouillon est effacé, l'offre aura de toute façon expiré. */
  private expireSession(): void {
    this.idle.set(true);
    clearDraft(this.offerId());
  }

  /** Envoie un voyageur à Duffel sur l'offre courante (identifiant relu au moment de l'envoi : le tarif a pu changer). */
  private persistTraveler(position: number): Observable<unknown> {
    const offer = this.offer();
    const info: ITravelerInfo | undefined = this.travelerInfo()[position];
    const passenger = offer?.passengers[position];
    if (!offer || !info || !passenger) {
      return EMPTY;
    }
    return this.api.updatePassenger(offer.id, passenger.id, toPassengerUpdate(info)).pipe(
      tap((updated) => {
        this.replaceOffer(updated);
        this.saveState.update((all) => Object.fromEntries(Object.entries(all).filter(([key]) => Number(key) !== position)));
      }),
      catchError(() => {
        this.saveState.update((all) => ({ ...all, [position]: 'failed' }));
        return EMPTY;
      }),
    );
  }

  /** Remplace l'offre courante par celle rendue par le backend. */
  private replaceOffer(offer: IFlightOfferDetails): void {
    // Autre tarif : autres identifiants de passagers et de sièges, les choix ne valent plus.
    if (offer.id !== this.offer()?.id) {
      this.seats.set([]);
    }
    if (offer.id === this.baseOffer()?.id) {
      this._offer.set({ data: offer });
      this.upgrade.set(null);
    } else {
      this.upgrade.set(offer);
    }
  }

  /**
   * Reprise après rechargement : saisies, tarif choisi (relu chez Duffel) puis étape. Une étape n'est rendue
   * que si les précédentes sont valides, sinon on revient à « Vos informations », erreurs affichées.
   */
  private restore(base: IFlightOfferDetails): void {
    const draft = loadDraft(this.offerId());
    if (!draft) {
      this.restored.set(true);
      return;
    }
    this.travelerInfo.set(draft.travelers);
    // Brouillon sans coordonnées : on garde celles de l'onglet.
    this.contact.update((known) => ({ email: draft.contact.email || known.email, phone: draft.contact.phone || known.phone }));
    this.seats.set(draft.seats);
    this.bags.set(draft.bags);
    this.paymentAttempt.set(draft.paymentAttempt);
    const resume = (fareLost = false): void => {
      let target = this.steps().includes(draft.step) ? draft.step : 'info';
      // Tarif choisi expiré : on retourne le choisir de nouveau (l'étape cède sa place s'il n'y a plus de montée en gamme).
      if (fareLost && STEPS.indexOf(target) > STEPS.indexOf('ticket')) {
        target = 'ticket';
        this.fareFailed.set(true);
      }
      if (target !== 'info' && !this.infoFilled()) {
        this.attempted.set(true);
        this.contactForm().markAsTouched();
        this.step.set('info');
      } else {
        this.step.set(target);
      }
      this.resyncTravelers();
      this.restored.set(true);
    };
    if (!draft.fareOfferId || draft.fareOfferId === base.id) {
      resume();
      return;
    }
    // Tarif expiré ou retiré : retour à l'offre de départ, les voyageurs y sont renvoyés.
    this.pendingFare.set(draft.fareOfferId);
    this.api
      .getOffer(draft.fareOfferId)
      .pipe(
        finalize(() => this.pendingFare.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: (offer) => this.upgrade.set(offer), error: () => resume(true), complete: () => resume() });
  }

  /** Voyageurs saisis mais absents de l'offre Duffel (enregistrement interrompu par le rechargement) : renvoyés. */
  private resyncTravelers(): void {
    const passengers = this.offer()?.passengers ?? [];
    for (const [position, info] of Object.entries(this.travelerInfo())) {
      const passenger = passengers[Number(position)];
      if (passenger && (passenger.given_name !== info.given_name || passenger.family_name !== info.family_name)) {
        this.retrySave(Number(position));
      }
    }
  }

  /** Chaque voyageur saisi, et des coordonnées valides. */
  private infoFilled(): boolean {
    return this.travelers().every((t) => this.travelerFilled(t.position)) && this.contactForm().valid();
  }

  /** Saisies complètes et toutes enregistrées chez Duffel. */
  private infoComplete(): boolean {
    const state = this.saveState();
    return this.infoFilled() && this.travelers().every((t) => !state[t.position]);
  }

  private goTo(step: TStep): void {
    this.step.set(step);
    this.scroller.scrollToPosition([0, 0]);
  }

  /** Bagages inclus pour un voyageur sur un vol : le plus petit franchissement de ses segments. */
  private bagsFor(slice: IOfferSlice, passengerId: string): Map<TBaggageType, number> {
    const perSegment = slice.segments.map((segment) => {
      const bags = segment.passengers.find((p) => p.passenger_id === passengerId)?.baggages ?? [];
      return new Map(BAGGAGE_TYPES.map((t) => [t, bags.filter((b) => b.type === t).reduce((sum, b) => sum + b.quantity, 0)]));
    });
    return new Map(BAGGAGE_TYPES.map((t) => [t, perSegment.length ? Math.min(...perSegment.map((m) => m.get(t) ?? 0)) : 0]));
  }
}

function toPassengerUpdate({ given_name, family_name, loyalty_programme_accounts }: ITravelerInfo): IPassengerUpdate {
  return { given_name, family_name, loyalty_programme_accounts };
}
