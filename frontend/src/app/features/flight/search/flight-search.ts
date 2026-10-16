/* eslint-disable @angular-eslint/no-input-rename */
import { Component, computed, effect, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { HttpErrorResponse, HttpStatusCode, httpResource } from '@angular/common/http';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button, Pagination, Spinner } from '@shared/ui';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, EMPTY, map, switchMap, timer } from 'rxjs';
import { IFlightSearchResult, TTripType } from '@shared/models';
import { SearchSkeleton } from '@features/flight/search/skeleton/search-skeleton';
import { SearchSummary } from '@features/flight/search/summary/search-summary';
import { FlightSearchFilter } from '@features/flight/search/filter/flight-search-filter';
import { FlightOfferCard } from '@features/flight/search/offer-card/offer-card';
import { IFlightOffer } from '@shared/models/flight.model';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroFunnel, heroMagnifyingGlass } from '@ng-icons/heroicons/outline';
import { FlightOfferDetails } from '@features/flight/search/offer-summary/offer-summary';
import { APP_CONFIG } from '@core/config/app-config.token';
import { SearchStore } from '@core/search/search.store';
import { parseSearchParams } from './search-params';
import { CurrencyStore } from '@core/money/currency.store';
import { applyFlightFilter, IFlightFilter, NO_FLIGHT_FILTER, resetFlightFilter } from './filter/flight-filter.util';

const FILTER_DEBOUNCE_MS = 500;
const FILTER_LOADER_MS = 1_500;
const PAGE_SIZE = 15;

@Component({
  selector: 'bip-search',
  templateUrl: './flight-search.html',
  host: { class: 'flex flex-col p-0' },
  providers: [provideIcons({ heroFunnel, heroMagnifyingGlass })],
  imports: [
    Button,
    TranslocoDirective,
    SearchSkeleton,
    SearchSummary,
    FlightSearchFilter,
    FlightOfferCard,
    Pagination,
    NgIcon,
    FlightOfferDetails,
    Spinner,
  ],
})
export class FlightSearch {
  readonly tripType = input<TTripType | undefined>(undefined, { alias: 't' });
  readonly origin = input<string | undefined>(undefined, { alias: 'o' });
  readonly destination = input<string | undefined>(undefined, { alias: 'd' });
  readonly from = input<string | undefined>(undefined);
  readonly to = input<string | undefined>(undefined);
  readonly adults = input(1, { alias: 'adt' });

  readonly children = input(0, { alias: 'chd' });
  readonly infants = input(0, { alias: 'inf' });
  readonly cabin = input('economy');
  readonly airlineParam = input<string | undefined>(undefined, { alias: 'ax' });

  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  /**
   * Recherche relancée à chaque changement de l'URL : mêmes noms de paramètres côté front et côté API.
   * Tant qu'un critère obligatoire manque, la ressource reste au repos (pas d'appel).
   */
  private readonly searchResource = httpResource<IFlightSearchResult>(() => {
    const [t, o, d, from, to] = [this.tripType(), this.origin(), this.destination(), this.from(), this.to()];
    if (!t || !o || !d || !from) {
      return undefined;
    }
    const optional = { to: t === 'round' ? to : undefined, adt: this.adults(), chd: this.children(), inf: this.infants(), cabin: this.cabin() };
    // Un paramètre absent de l'URL arrive en `undefined` (liaison des entrées par le routeur), même si l'entrée
    // a une valeur par défaut : on ne l'envoie pas, le backend applique la sienne.
    const params: Record<string, string | number> = { t, o, d, from };
    for (const [key, value] of Object.entries(optional)) {
      if (value !== undefined && value !== null && value !== '') {
        params[key] = value;
      }
    }
    return { url: `${this.apiBaseUrl}/flights/search`, params };
  });
  readonly loading = this.searchResource.isLoading;
  readonly offers = computed(() => this.searchResource.hasValue() ? this.searchResource.value().data.offers : undefined);
  readonly hasOffers = computed(() => (this.offers()?.length ?? 0) > 0);
  /** Critères du panneau de filtres ; remis à zéro à chaque nouvelle recherche. */
  protected readonly filter = signal<IFlightFilter>(NO_FLIGHT_FILTER);
  /** Critères appliqués à la liste : ceux du panneau, une fois la saisie posée et le chargement affiché. */
  private readonly appliedFilter = signal<IFlightFilter>(NO_FLIGHT_FILTER);
  protected readonly applyingFilter = signal(false);
  private readonly currencyStore = inject(CurrencyStore);
  /** Offres affichées : le budget est dans la devise d'affichage, chaque prix y est converti avant comparaison. */
  protected readonly shownOffers = computed(() => {
    const target = this.currencyStore.selected();
    return applyFlightFilter(this.offers() ?? [], this.appliedFilter(), (amount, currency) => this.currencyStore.convert(amount, currency, target));
  });
  protected readonly pageSize = PAGE_SIZE;
  /** Page courante, à partir de 1 ; revient à la première dès que la liste filtrée change. */
  protected readonly page = signal(1);
  protected readonly pageOffers = computed(() => {
    const start = (this.page() - 1) * PAGE_SIZE;
    return this.shownOffers().slice(start, start + PAGE_SIZE);
  });
  private readonly offerList = viewChild<ElementRef<HTMLElement>>('offerList');
  private readonly viewportScroller = inject(ViewportScroller);
  /** Panne côté API (hors critères refusés, qui renvoient à l'accueil) : on propose de relancer. */
  readonly failed = computed(() => {
    const error = this.searchResource.error();
    return !!error && !this.isRejected(error);
  });

  readonly selected = signal<IFlightOffer | undefined>(undefined);

  /** Critères relus de l'URL ; la garde garantit qu'ils sont valides. */
  protected readonly search = computed(() =>
    parseSearchParams({
      t: this.tripType(),
      o: this.origin(),
      d: this.destination(),
      from: this.from(),
      to: this.to(),
      adt: this.adults(),
      chd: this.children(),
      inf: this.infants(),
      cabin: this.cabin(),
    }),
  );
  protected readonly editing = signal(false);

  private readonly router = inject(Router);
  private readonly searchStore = inject(SearchStore);

  constructor() {
    effect(() => {
      this.shownOffers();
      this.page.set(1);
    });

    // Filtres : 500 ms sans changement, puis 1,5 s de chargement avant de mettre la liste à jour.
    // Un changement pendant le chargement le relance ; revenir aux critères déjà appliqués l'annule.
    toObservable(this.filter)
      .pipe(
        debounceTime(FILTER_DEBOUNCE_MS),
        switchMap((filter) => {
          if (filter === this.appliedFilter()) {
            this.applyingFilter.set(false);
            return EMPTY;
          }
          this.applyingFilter.set(true);
          return timer(FILTER_LOADER_MS).pipe(map(() => filter));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((filter) => {
        this.appliedFilter.set(filter);
        this.applyingFilter.set(false);
      });

    let previousSearch: string | undefined;
    effect(() => {
      const search = this.search();
      if (search) {
        this.searchStore.save({ ...search, cabin: search.cabin[0] });
      }
      // Nouvelle recherche : les filtres de la précédente ne valent plus (la première fois, l'URL peut en poser).
      const key = JSON.stringify(search);
      if (previousSearch !== undefined && key !== previousSearch) {
        // La limite d'escales choisie est gardée ; même objet des deux côtés : pas de chargement.
        const next = resetFlightFilter(this.filter());
        this.filter.set(next);
        this.appliedFilter.set(next);
      }
      previousSearch = key;
    });
    // La garde de route filtre déjà les URL invalides ; reste le cas où le backend refuse les critères (400).
    effect(() => {
      const error = this.searchResource.error();
      if (error && this.isRejected(error)) {
        this.backToHome();
      }
    });
  }

  protected backToHome(): void {
    void this.router.navigate(['/flight']);
  }

  protected retry(): void {
    this.searchResource.reload();
  }

  private isRejected(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === HttpStatusCode.BadRequest;
  }

  /** Changement de page : la page et la liste repartent du haut. */
  protected goToPage(page: number): void {
    this.page.set(page);
    this.offerList()?.nativeElement.scrollTo({ top: 0 });
    this.viewportScroller.scrollToPosition([0, 0], { behavior: 'smooth' });
  }

  protected openOfferSummary(offer: IFlightOffer): void {
    if (offer) {
      this.selected.set(offer);
    }
  }

  protected closeOfferSummary(): void {
    this.selected.set(undefined);
  }

  protected offerSelection() {
    const s = this.selected();
    if (!s) {
      return;
    }
    void this.router.navigate(['/flight', btoa(s.id)]);
  }
}
