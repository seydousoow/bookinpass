import { Component, computed, effect, inject, input, PLATFORM_ID, signal } from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { IAirlineFilter, IBagFilter, ISelectOption } from '@shared/models';
import { SelectionModel } from '@angular/cdk/collections';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Badge, Button, Checkbox, Overlay, Viewport } from '@shared/ui';
import { TranslocoDirective } from '@jsverse/transloco';
import { IFlightOffer } from '@shared/models/flight.model';
import { CurrencyStore } from '@core/money/currency.store';
import { PriceRange, Slider } from '@shared/ui/slider/slider';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroFunnelSolid, heroXMarkSolid } from '@ng-icons/heroicons/solid';

@Component({
  selector: 'bip-flight-search-filter',
  templateUrl: './flight-search-filter.html',
  host: { '(document:keydown.escape)': 'onEscape()' },
  imports: [Checkbox, TranslocoDirective, Badge, Slider, Button, NgIcon, CdkTrapFocus, Overlay],
  providers: [provideIcons({ heroFunnelSolid, heroXMarkSolid })],
})
export class FlightSearchFilter {
  readonly loading = input.required<boolean>();
  readonly offers = input.required<readonly IFlightOffer[]>();
  readonly querySelection = input<string>();

  /** Feuille plein écran ouverte — sans effet au-delà du point de rupture mobile (`md:`). */
  protected readonly open = signal(false);
  protected readonly maxConnection = signal(2);
  protected readonly baggage = signal<IBagFilter>({ cabin: false, hold: false });
  protected budget = signal<PriceRange>({ min: 0, max: 1_000_000 });
  protected readonly airlineSelection = new SelectionModel<string>(true, [], true, (o, b) => Object.is(o, b));
  protected readonly connectionOptions: readonly ISelectOption<number>[] = [0, 1, 2].map((value) => ({ label: 'search.filter.stops.options', value }));
  protected readonly bagOptions = Array.from<'cabin' | 'hold'>(['cabin', 'hold']).map((value) => ({ label: `search.filter.bags.${value}`, value }));
  protected readonly currency = computed(() => this.offers().map((u) => u.total_currency)[0]);
  protected readonly airlines = computed<readonly IAirlineFilter[]>(() => {
    const airlines = new Map<string, IAirlineFilter>();
    for (const offer of this.offers()) {
      const airline = offer.owner;
      const iata = airline.iata_code;
      if (iata != null) {
        const existing = airlines.get(iata);
        if (existing) {
          airlines.set(iata, { ...existing, offerCount: existing.offerCount + 1 });
          continue;
        }
        airlines.set(iata, { code: iata, name: airline.name, logoUrl: (airline.logo_symbol_url || airline.logo_lockup_url)!, offerCount: 1 });
      }
    }
    return [...airlines.values()].sort((a, b) => a.name.localeCompare(b.name));
  });
  private readonly viewport = inject(Viewport);
  /**
   * Vrai en dessous du point de rupture `md:`. Piloté en JS (et non en CSS
   * seul) car la sémantique d'accessibilité (rôle dialogue, aria-hidden,
   * piège à focus) doit changer avec la mise en page : sur bureau, le
   * panneau est une barre latérale toujours visible, jamais une boîte de
   * dialogue à masquer du lecteur d'écran.
   */
  protected readonly isSheetActive = computed(() => this.viewport.isMobile() && this.open());
  protected readonly isSheetHidden = computed(() => this.viewport.isMobile() && !this.open());

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

  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  constructor() {
    effect(() => {
      const query = this.querySelection();
      if (query) {
        this.airlineSelection.setSelection(...query.split(','));
      }
      this.budget.update((old) => ({ ...old, max: this.maxPrice() ?? old.max }));
    });

    // Verrou de défilement de la page tant que la feuille mobile est ouverte.
    effect(() => {
      if (!this.isBrowser) {
        return;
      }
      this.document.body.style.overflow = this.isSheetActive() ? 'hidden' : '';
    });
  }

  get step(): number {
    return this.currencyStore.isContractCurrency() ? 10_000 : 10;
  }

  toggle(): void {
    this.open.update((old) => !old);
  }

  protected onBagFilter(key: 'cabin' | 'hold', checked: boolean) {
    this.baggage.update((old) => ({ ...old, [key]: checked }));
  }

  protected close(): void {
    this.open.set(false);
  }

  protected onEscape(): void {
    if (this.open()) {
      this.close();
    }
  }

  private roundPriceUp(amount: number, currency: string): number {
    const step = currency === 'XOF' ? 10_000 : 10;
    return Math.ceil(amount / step) * step;
  }
}
