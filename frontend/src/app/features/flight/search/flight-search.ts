/* eslint-disable @angular-eslint/no-input-rename */
import { Component, computed, inject, input, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button, Modal } from '@shared/ui';
import { IFlightSearchResult, IFlightSearch, TTripType } from '@shared/models';
import { SearchSkeleton } from '@features/flight/search/skeleton/search-skeleton';
import { SearchSummary } from '@features/flight/search/summary/search-summary';
import { FlightSearchFilter } from '@features/flight/search/filter/flight-search-filter';
import { FlightOfferCard } from '@features/flight/search/offer-card/offer-card';
import { IFlightOffer } from '@shared/models/flight.model';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroFunnel } from '@ng-icons/heroicons/outline';
import { FlightOfferDetails } from '@features/flight/search/offer-summary/offer-summary';
import { APP_CONFIG } from '@core/config/app-config.token';

@Component({
  selector: 'bip-search',
  templateUrl: './flight-search.html',
  host: { class: 'block p-0 h-[calc(100vh-65px)]' },
  providers: [provideIcons({ heroFunnel })],
  imports: [
    Modal,
    Button,
    TranslocoDirective,
    SearchSkeleton,
    SearchSummary,
    FlightSearchFilter,
    FlightOfferCard,
    ScrollingModule,
    NgIcon,
    FlightOfferDetails,
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

  readonly invalid = signal(false);

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
  readonly result = computed(() => (this.searchResource.hasValue() ? this.searchResource.value() : undefined));
  readonly offers = computed(() => this.result()?.data?.offers ?? []);
  readonly hasOffers = computed(() => this.offers().length > 0);

  readonly selected = signal<IFlightOffer | undefined>(undefined);

  protected search = signal<IFlightSearch>({
    tripType: 'oneway',
    cabin: ['economy'],
    dates: { start: null, end: null },
    destination: '',
    origin: '',
    passengers: { adults: 1, children: 0, infants: 0 },
  });

  private readonly router = inject(Router);

  protected backToHome(): void {
    void this.router.navigate(['/']);
  }

  protected onSearchInvalid(invalid: boolean): void {
    this.invalid.set(invalid);
  }

  protected openOfferSummary(offer: IFlightOffer): void {
    if (offer) {
      this.selected.set(offer);
    }
  }

  protected closeOfferSummary(): void {
    this.selected.set(undefined);
  }

  protected trackOffer(_: number, offer: IFlightOffer): string {
    return offer.id;
  }

  protected offerSelection() {
    const s = this.selected();
    if (!s) {
      return;
    }
    void this.router.navigate(['flight', btoa(s.id)]);
  }
}
