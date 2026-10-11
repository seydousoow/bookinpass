import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { httpResource } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { email, form, FormField, required } from '@angular/forms/signals';
import { TranslocoDirective } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { TranslocoCurrencyPipe, TranslocoLocaleService } from '@jsverse/transloco-locale';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheck, heroChevronDown, heroInformationCircle, heroNoSymbol, heroUser } from '@ng-icons/heroicons/outline';
import { Button, Modal, Spinner, TextInput } from '@shared/ui';
import { IFlightOfferResponse, IOfferSlice, TBaggageType, TPassengerType } from '@shared/models';
import { ITravelerInfo, TravelerForm } from './traveler-form/traveler-form';
import { Router } from '@angular/router';

interface IBagLine {
  readonly type: TBaggageType;
  readonly quantity: number;
}

interface ITravelerCard {
  readonly id: string;
  readonly type: TPassengerType;
  /** Rang du voyageur au sein de son type : « Adulte n° 1 », « Enfant n° 1 »… */
  readonly index: number;
  /** Bagages identiques sur tous les vols. */
  readonly everyFlight: readonly IBagLine[];
  /** Bagages propres à un vol — uniquement les écarts par rapport à `everyFlight`. */
  readonly perSlice: readonly { readonly destination: string; readonly bags: readonly IBagLine[] }[];
}

interface IContact {
  email: string;
  phone: string;
}

const BAGGAGE_TYPES: readonly TBaggageType[] = ['checked', 'carry_on'];

@Component({
  selector: 'bip-flight-offer',
  templateUrl: './flight-offer.html',
  host: { class: 'container min-h-xl w-full max-w-6xl mx-auto p-2 block' },
  imports: [NgTemplateOutlet, Spinner, Modal, TranslocoDirective, Button, NgIcon, TranslocoCurrencyPipe, TextInput, FormField, TravelerForm],
  providers: [provideIcons({ heroUser, heroChevronDown, heroCheck, heroInformationCircle, heroNoSymbol })],
})
export class FlightOffer {
  readonly offerId = input<string>('', { transform: (v) => atob(v as string) });
  protected readonly steps = ['info', 'ticket', 'extras', 'seat', 'pay'] as const;
  protected readonly currentStep = 0;
  protected readonly firstSlice = computed(() => this.offer()?.slices[0]);
  protected readonly isRoundTrip = computed(() => (this.offer()?.slices.length ?? 0) > 1);
  /** Nombre de voyageurs par type, dans l'ordre adulte → enfant → bébé. */
  protected readonly passengerGroups = computed(() => {
    const counts = new Map<TPassengerType, number>();
    for (const { type } of this.offer()?.passengers ?? []) {
      const key = type ?? 'adult';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return (['adult', 'child', 'infant_without_seat'] as const)
      .filter((type) => counts.has(type))
      .map((type) => ({ type, count: counts.get(type)! }));
  });
  protected readonly travelers = computed((): ITravelerCard[] => {
    const offer = this.offer();
    if (!offer) {
      return [];
    }
    const seen = new Map<TPassengerType, number>();
    return offer.passengers.map(({ id, type }) => {
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

      return { id, type: key, index, everyFlight, perSlice };
    });
  });
  /** Date du dernier vol : les compagnies vérifient l'âge des voyageurs à cette date. */
  protected readonly lastFlightDate = computed(() => {
    const segments = this.offer()?.slices.at(-1)?.segments ?? [];
    return segments.at(-1)?.departing_at ?? '';
  });
  /** Identités saisies, par identifiant de passager Duffel. */
  protected readonly travelerInfo = signal<Readonly<Record<string, ITravelerInfo>>>({});
  /** Voyageur dont la modale de saisie est ouverte. */
  protected readonly editing = signal<ITravelerCard | null>(null);
  protected readonly taxesOpened = signal(false);
  protected readonly fractionDigits = computed(() => (this.offer()?.total_currency?.toUpperCase() === 'XOF' ? 0 : 2));
  protected readonly contactForm = form(signal<IContact>({ email: '', phone: '' }), (path) => {
    required(path.email, { message: 'common.required-field' });
    email(path.email, { message: 'flight.offer.contact.email-invalid' });
  });
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly _offer = httpResource<IFlightOfferResponse>(() => `${this.apiBaseUrl}/flights/offers/${encodeURIComponent(this.offerId())}`);
  readonly isLoading = this._offer.isLoading;
  readonly error = computed(() => this._offer.status() === 'error');
  readonly offer = computed(() => this._offer.value()?.data);
  private readonly localeService = inject(TranslocoLocaleService);
  private readonly locale = toSignal(this.localeService.localeChanges$, { initialValue: this.localeService.getLocale() });
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

  protected returnHomePage() {
    // todo: get search history and rebuild search path params
    // todo: if missing, then return home
    void this.router.navigate(['search']);
  }

  protected saveTraveler(passengerId: string, info: ITravelerInfo): void {
    this.travelerInfo.update((all) => ({ ...all, [passengerId]: info }));
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
