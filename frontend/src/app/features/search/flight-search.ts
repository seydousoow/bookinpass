/* eslint-disable @angular-eslint/no-input-rename */
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button, Modal } from '@shared/ui';
import { FlightSearchResult, IFlightSearch, TripType } from '@shared/models';
import { SearchSkeleton } from '@core/search/skeleton/search-skeleton';
import { SearchSummary } from '@core/search/summary/search-summary';
import { FlightSearchFilter } from '@core/search/filter/flight-search-filter';
import { FlightOfferCard } from '@core/search/offer-card/offer-card';
import { FlightOffer } from '@shared/models/flight.model';
import { ScrollingModule } from '@angular/cdk/scrolling';

@Component({
  selector: 'bip-search',
  templateUrl: './flight-search.html',
  host: { class: 'block p-0 h-[calc(100vh-65px)]' },
  imports: [
    Modal,
    Button,
    TranslocoDirective,
    SearchSkeleton,
    SearchSummary,
    FlightSearchFilter,
    FlightOfferCard,
    ScrollingModule
  ],
})
export class FlightSearch {
  readonly tripType = input<TripType | undefined>(undefined, { alias: 't' });
  readonly origin = input<string | undefined>(undefined, { alias: 'o' });
  readonly destination = input<string | undefined>(undefined, { alias: 'd' });
  readonly from = input<string | undefined>(undefined);
  readonly to = input<string | undefined>(undefined);
  readonly adults = input(1, { alias: 'adt' });

  readonly children = input(0, { alias: 'chd' });
  readonly infants = input(0, { alias: 'inf' });
  readonly cabin = input('economy');
  readonly airlineParam = input<string | undefined>(undefined, { alias: 'ax' });

  readonly loading = signal(true);
  readonly invalid = signal(false);

  readonly result = signal<FlightSearchResult | undefined>(undefined);
  readonly offers = computed(() => this.result()?.data?.offers ?? []);
  readonly hasOffers = computed(() => this.offers().length > 0);

  protected search = signal<IFlightSearch>({
    tripType: 'oneway',
    cabin: ['economy'],
    dates: { start: null, end: null },
    destination: '',
    origin: '',
    passengers: { adults: 1, children: 0, infants: 0 },
  });

  private readonly router = inject(Router);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    fetch('/duffel/response/flight-dss-cdg-return-21_10-21_11.json')
      .then((r) => r.json() as Promise<FlightSearchResult>)
      .then((data) => {
        this.result.set(data);
        this.loading.set(false);
      });
  }

  protected backToHome(): void {
    void this.router.navigate(['/']);
  }

  protected onSearchInvalid(invalid: boolean): void {
    this.invalid.set(invalid);
  }

  protected open(offer: FlightOffer): void {
    console.log(offer);
  }

  protected trackOffer(_: number, offer: FlightOffer): string {
    return offer.id;
  }
}
