import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { AirlineFilter, BagFilter, SelectOption } from '@shared/models';
import { SelectionModel } from '@angular/cdk/collections';
import { Badge, Checkbox } from '@shared/ui';
import { TranslocoDirective } from '@jsverse/transloco';
import { FlightOffer } from '@shared/models/flight.model';
import { CurrencyStore } from '@core/money/currency.store';
import { PriceRange, Slider } from '@shared/ui/slider/slider';

@Component({
  selector: 'bip-flight-search-filter',
  templateUrl: './flight-search-filter.html',
  imports: [Checkbox, TranslocoDirective, Badge, Slider],
})
export class FlightSearchFilter {
  readonly loading = input.required<boolean>();
  readonly offers = input.required<readonly FlightOffer[]>();
  readonly querySelection = input<string>();

  protected readonly maxConnection = signal(2);
  protected readonly baggage = signal<BagFilter>({ cabin: false, hold: false });
  protected budget = signal<PriceRange>({ min: 0, max: 1_000_000 });
  protected readonly airlineSelection = new SelectionModel<string>(true, [], true, (o, b) => Object.is(o, b));

  protected readonly connectionOptions: readonly SelectOption<number>[] = [0, 1, 2].map((value) => ({ label: 'search.filter.stops.options', value }));
  protected readonly bagOptions = Array.from<'cabin' | 'hold'>(['cabin', 'hold']).map((value) => ({ label: `search.filter.bags.${value}`, value }));
  protected readonly currency = computed(() => this.offers().map((u) => u.total_currency)[0]);
  protected readonly airlines = computed<readonly AirlineFilter[]>(() => {
    const airlines = new Map<string, AirlineFilter>();
    for (const offer of this.offers()) {
      const airline = offer.owner;
      const existing = airlines.get(airline.iata_code);
      if (existing) {
        airlines.set(airline.iata_code, { ...existing, offerCount: existing.offerCount + 1 });
        continue;
      }
      airlines.set(airline.iata_code, { code: airline.iata_code, name: airline.name, logoUrl: airline.logo_symbol_url, offerCount: 1 });
    }
    return [...airlines.values()].sort((a, b) => a.name.localeCompare(b.name));
  });
  private readonly currencyStore = inject(CurrencyStore);
  protected readonly maxPrice = computed(() => {
    const offers = this.offers();
    if (offers.length === 0) {
      return 0;
    }
    const displayCurrency = this.currencyStore.selected();
    const prices = offers
      .map((offer) => this.currencyStore.convert(Number(offer.total_amount), offer.total_currency, displayCurrency))
      .filter((price): price is number => price !== null);
    return prices.length === 0 ? 0 : this.roundPriceUp(Math.max(...prices), displayCurrency);
  });

  constructor() {
    effect(() => {
      const query = this.querySelection();
      if (query) {
        this.airlineSelection.setSelection(...query.split(','));
      }
      this.budget.update((old) => ({ ...old, max: this.maxPrice() ?? old.max }));
    });
  }

  get step(): number {
    return this.currencyStore.isContractCurrency() ? 10_000 : 10;
  }

  protected onBagFilter(key: 'cabin' | 'hold', checked: boolean) {
    this.baggage.update((old) => ({ ...old, [key]: checked }));
  }

  private roundPriceUp(amount: number, currency: string): number {
    const step = currency === 'XOF' ? 10_000 : 10;
    return Math.ceil(amount / step) * step;
  }
}
