import { Component, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import { IAirlineFilter, ISelectOption } from '@shared/models';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Badge, Button, Checkbox, lockScrollWhile, Overlay, RadioGroup, Viewport } from '@shared/ui';
import { TranslocoDirective } from '@jsverse/transloco';
import { IFlightOffer } from '@shared/models/flight.model';
import { CurrencyStore } from '@core/money/currency.store';
import { PriceRange, Slider } from '@shared/ui/slider/slider';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroFunnelSolid, heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { IFlightFilter, isFlightFiltering, NO_FLIGHT_FILTER, resetFlightFilter, STOP_OPTIONS, toggleAirline } from './flight-filter.util';
import { sellingTotal } from '@core/money/pricing';

@Component({
  selector: 'bip-flight-search-filter',
  templateUrl: './flight-search-filter.html',
  host: { '(document:keydown.escape)': 'onEscape()' },
  imports: [Checkbox, RadioGroup, TranslocoDirective, Badge, Slider, Button, NgIcon, CdkTrapFocus, Overlay],
  providers: [provideIcons({ heroFunnelSolid, heroXMarkSolid })],
})
export class FlightSearchFilter {
  readonly loading = input.required<boolean>();
  readonly offers = input.required<readonly IFlightOffer[]>();
  /** Compagnies présélectionnées par l'URL (`ax=AF,SN`). */
  readonly querySelection = input<string>();
  /** Filtres en cours d'application : le panneau est figé sous un voile jusqu'à la mise à jour de la liste. */
  readonly disabled = input(false);
  /** Critères retenus : la page filtre la liste avec. */
  readonly value = model<IFlightFilter>(NO_FLIGHT_FILTER);

  /** Feuille plein écran ouverte — sans effet au-delà du point de rupture mobile (`md:`). */
  protected readonly open = signal(false);
  protected readonly filtering = computed(() => isFlightFiltering(this.value()));
  protected readonly stopOptions: readonly ISelectOption<number>[] = STOP_OPTIONS.map((value) => ({ label: `search.filter.stops.max.${value}`, value }));
  protected readonly bagOptions = [
    { key: 'cabinBag', label: 'search.filter.bags.cabin' },
    { key: 'holdBag', label: 'search.filter.bags.hold' },
  ] as const;
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
  /** Le budget s'exprime dans la devise choisie par l'utilisateur, comme les prix affichés. */
  protected readonly currency = this.currencyStore.selected;
  protected readonly step = computed(() => (this.currencyStore.isContractCurrency() ? 10_000 : 10));
  protected readonly maxPrice = computed(() => {
    const displayCurrency = this.currencyStore.selected();
    const prices = this.offers()
      .map((offer) => this.currencyStore.convert(sellingTotal(offer), offer.total_currency, displayCurrency))
      .filter((price): price is number => price !== null);
    const step = this.step();
    return prices.length === 0 ? 0 : Math.ceil(Math.max(...prices) / step) * step;
  });
  /** Sans plafond choisi, le curseur couvre toute la fourchette des offres. */
  protected readonly budget = computed((): PriceRange => this.value().price ?? { min: 0, max: this.maxPrice() });


  constructor() {
    effect(() => {
      const query = this.querySelection();
      if (query) {
        untracked(() => this.value.update((f) => ({ ...f, airlines: query.split(',') })));
      }
    });

    // Autre devise d'affichage : l'ancienne fourchette de prix n'a plus de sens.
    effect(() => {
      this.currency();
      if (untracked(this.value).price !== null) {
        untracked(() => this.value.update((f) => ({ ...f, price: null })));
      }
    });

    // Verrou de défilement de la page tant que la feuille mobile est ouverte.
    lockScrollWhile(this.isSheetActive);
  }

  toggle(): void {
    this.open.update((old) => !old);
  }

  reset(): void {
    this.value.update(resetFlightFilter);
  }

  protected setMaxStops(stops: number | null): void {
    if (stops !== null) {
      this.value.update((f) => ({ ...f, maxStops: stops }));
    }
  }

  protected setBag(key: 'cabinBag' | 'holdBag', checked: boolean): void {
    this.value.update((f) => ({ ...f, [key]: checked }));
  }

  protected setBudget(range: PriceRange): void {
    const full = range.min <= 0 && range.max >= this.maxPrice();
    this.value.update((f) => ({ ...f, price: full ? null : range }));
  }

  protected isAirlineChecked(code: string): boolean {
    const selected = this.value().airlines;
    return selected.length === 0 || selected.includes(code);
  }

  protected setAirline(code: string, checked: boolean): void {
    const all = this.airlines().map((a) => a.code);
    this.value.update((f) => ({ ...f, airlines: toggleAirline(f.airlines, all, code, checked) }));
  }

  protected close(): void {
    this.open.set(false);
  }

  protected onEscape(): void {
    if (this.open()) {
      this.close();
    }
  }
}
