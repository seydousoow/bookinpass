/* eslint-disable @angular-eslint/no-input-rename */
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse, httpResource, HttpStatusCode } from '@angular/common/http';
import { Router } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { DatePipe } from '@core/i18n/date.pipe';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroAdjustmentsHorizontal, heroMagnifyingGlass } from '@ng-icons/heroicons/outline';
import { APP_CONFIG } from '@core/config/app-config.token';
import { CurrencyStore } from '@core/money/currency.store';
import { StaySearchStore } from '@core/search/stay-search.store';
import { ISelectOption, IStayDestination, IStayResult, IStaySearchResponse } from '@shared/models';
import { Button, Modal, Select, Viewport } from '@shared/ui';
import { StaySearchForm } from '../search-form/stay-search-form';
import { StayCard } from './stay-card/stay-card';
import { StayFilter } from './stay-filter/stay-filter';
import { applyStayFilter, IStayFilter, NO_STAY_FILTER, sortStays, STAY_SORTS, TStaySort } from './stay-filter/stay-filter.util';
import { StayResultsSkeleton } from './stay-results-skeleton';
import { nightsBetween, parseStaySearchParams, toStayQueryParams } from './stay-search-params';

/**
 * Résultats d'une recherche d'hébergement. L'URL est la source de vérité (`/stays/search?l=DKR&in=…`) :
 * la garde de route écarte une URL invalide, la recherche part à chaque changement de paramètres.
 */
@Component({
  selector: 'bip-stay-search',
  templateUrl: './stay-search.html',
  host: { class: 'block' },
  providers: [provideIcons({ heroMagnifyingGlass, heroAdjustmentsHorizontal })],
  imports: [TranslocoDirective, DatePipe, NgIcon, Button, Modal, Select, StaySearchForm, StayCard, StayFilter, StayResultsSkeleton],
})
export class StaySearch {
  readonly location = input<string | undefined>(undefined, { alias: 'l' });
  readonly checkIn = input<string | undefined>(undefined, { alias: 'in' });
  readonly checkOut = input<string | undefined>(undefined, { alias: 'out' });
  readonly adults = input<string | undefined>(undefined, { alias: 'adt' });
  readonly children = input<string | undefined>(undefined, { alias: 'chd' });
  readonly rooms = input<string | undefined>(undefined);

  /** Critères relus de l'URL ; la garde garantit qu'ils sont valides. */
  protected readonly search = computed(() =>
    parseStaySearchParams({
      l: this.location(),
      in: this.checkIn(),
      out: this.checkOut(),
      adt: this.adults(),
      chd: this.children(),
      rooms: this.rooms(),
    }),
  );
  protected filter = signal<IStayFilter>(NO_STAY_FILTER);
  protected readonly sort = signal<TStaySort[]>(['price']);
  protected readonly editing = signal(false);
  /** Sur mobile, la modification s'ouvre dans une modale plutôt que sous le résumé. */
  protected readonly isMobile = inject(Viewport).isMobile;
  protected readonly dateFormat: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', timeZone: 'UTC' };
  protected readonly nights = computed(() => {
    const dates = this.search()?.dates;
    return dates?.start && dates.end ? nightsBetween(dates.start, dates.end) : 0;
  });
  private readonly currencyStore = inject(CurrencyStore);
  /** Le plafond de prix est dans la devise d'affichage : chaque tarif y est converti avant comparaison. */
  protected readonly shown = computed(() => {
    const target = this.currencyStore.selected();
    const filtered = applyStayFilter(this.results(), this.filter(), (amount, currency) => this.currencyStore.convert(amount, currency, target));
    return sortStays(filtered, this.sort()[0] ?? 'price');
  });
  protected readonly total = computed(() => this.results().length);
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly resource = httpResource<IStaySearchResponse>(() => {
    const search = this.search();
    if (!search) {
      return undefined;
    }
    return {url: `${this.apiBaseUrl}/stays/search`, params: toStayQueryParams(search)};
  });
  protected readonly loading = this.resource.isLoading;
  /** Panne côté API (hors critères refusés, qui renvoient à l'accueil des hébergements) : on propose de relancer. */
  protected readonly failed = computed(() => {
    const error = this.resource.error();
    return !!error && !isRejected(error);
  });
  protected readonly results = computed((): readonly IStayResult[] => (this.resource.hasValue() ? this.resource.value().data.results : []));
  private readonly transloco = inject(TranslocoService);
  protected readonly sortOptions = computed((): readonly ISelectOption<TStaySort>[] => {
    const lang = this.transloco.activeLang();
    return STAY_SORTS.map((value) => ({ label: this.transloco.translate(`stay.sort.${value}`, {}, lang), value }));
  });
  private readonly destination = httpResource<IStayDestination>(() => {
    const id = this.search()?.location;
    return id ? `${this.apiBaseUrl}/stays/destinations/${encodeURIComponent(id)}` : undefined;
  });
  /** Nom de la ville cherchée ; vide le temps de la lire. */
  protected readonly placeName = computed(() => (this.destination.hasValue() ? this.destination.value().name : ''));
  private readonly router = inject(Router);
  private readonly store = inject(StaySearchStore);

  constructor() {
    effect(() => {
      const search = this.search();
      if (search) {
        this.store.save(search);
        this.editing.set(false);
        this.filter.set(NO_STAY_FILTER);
      }
    });
    // Le backend refuse les critères (lieu inconnu…) : retour au formulaire.
    effect(() => {
      const error = this.resource.error();
      if (error && isRejected(error)) {
        void this.router.navigate(['/stays']);
      }
    });
  }

  protected retry(): void {
    this.resource.reload();
  }

  protected open(result: IStayResult): void {
    void this.router.navigate(['/stays', result.id]);
  }
}

function isRejected(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === HttpStatusCode.BadRequest;
}
