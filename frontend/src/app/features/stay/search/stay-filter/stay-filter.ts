import { Component, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { IStayResult, TStayAmenity } from '@shared/models/stay.model';
import { Badge } from '@shared/ui/badge/badge';
import { Button } from '@shared/ui/button/button';
import { Checkbox } from '@shared/ui/checkbox/checkbox';
import { Overlay } from '@shared/ui/overlay/overlay';
import { lockScrollWhile } from '@shared/ui/scroll-lock/scroll-lock';
import { Viewport } from '@shared/ui/viewport/viewport';
import { PriceRange, Slider } from '@shared/ui/slider/slider';
import { CurrencyStore } from '@core/money/currency.store';
import { isFiltering, IStayFilter, NO_STAY_FILTER, REVIEW_THRESHOLDS } from './stay-filter.util';
import { staySellingTotal } from '@core/money/pricing';

const STARS = [5, 4, 3, 2, 1] as const;

/**
 * Filtres de la liste : budget, étoiles, note des avis et équipements. Barre latérale sur bureau,
 * feuille plein écran sur mobile (mêmes règles d'accessibilité que les filtres de vols).
 */
@Component({
  selector: 'bip-stay-filter',
  templateUrl: './stay-filter.html',
  host: { '(document:keydown.escape)': 'close()' },
  imports: [TranslocoDirective, Checkbox, Badge, Slider, Button, NgIcon, CdkTrapFocus, Overlay],
  providers: [provideIcons({ heroXMarkSolid })],
})
export class StayFilter {
  readonly results = input.required<readonly IStayResult[]>();
  readonly value = model<IStayFilter>(NO_STAY_FILTER);

  protected readonly open = signal(false);
  protected readonly stars = STARS;
  protected readonly reviewThresholds = REVIEW_THRESHOLDS;
  protected readonly filtering = computed(() => isFiltering(this.value()));
  private readonly currencyStore = inject(CurrencyStore);
  /** Le budget s'exprime dans la devise choisie par l'utilisateur, comme les prix affichés. */
  protected readonly currency = this.currencyStore.selected;
  protected readonly step = computed(() => (this.currencyStore.isContractCurrency() ? 1_000 : 10));
  protected readonly maxPrice = computed(() => {
    const prices = this.results()
      .map((r) => this.currencyStore.convert(staySellingTotal(r.cheapest_rate_total_amount, r.cheapest_rate_currency), r.cheapest_rate_currency))
      .filter((price): price is number => price !== null);
    const step = this.step();
    return prices.length ? Math.ceil(Math.max(...prices) / step) * step : 0;
  });
  protected readonly budget = computed((): PriceRange => ({ min: 0, max: this.value().maxPrice ?? this.maxPrice() }));
  /** Équipements présents dans les résultats, avec le nombre d'établissements qui les offrent. */
  protected readonly amenities = computed(() => {
    const counts = new Map<TStayAmenity, number>();
    for (const { accommodation } of this.results()) {
      for (const { type } of accommodation.amenities ?? []) {
        counts.set(type, (counts.get(type) ?? 0) + 1);
      }
    }
    return [...counts].map(([type, count]) => ({ type, count })).sort((a, b) => b.count - a.count);
  });

  private readonly viewport = inject(Viewport);
  protected readonly isSheetActive = computed(() => this.viewport.isMobile() && this.open());
  protected readonly isSheetHidden = computed(() => this.viewport.isMobile() && !this.open());

  constructor() {
    // Autre devise d'affichage : l'ancien plafond n'a plus de sens.
    effect(() => {
      this.currency();
      if (untracked(this.value).maxPrice !== null) {
        this.value.update((f) => ({ ...f, maxPrice: null }));
      }
    });
    // Verrou de défilement de la page tant que la feuille mobile est ouverte.
    lockScrollWhile(this.isSheetActive);
  }

  toggle(): void {
    this.open.update((open) => !open);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected setBudget({ max }: PriceRange): void {
    this.value.update((f) => ({ ...f, maxPrice: max >= this.maxPrice() ? null : max }));
  }

  protected toggleStar(star: number, checked: boolean): void {
    this.value.update((f) => ({ ...f, stars: checked ? [...f.stars, star] : f.stars.filter((s) => s !== star) }));
  }

  protected setMinReview(score: number, checked: boolean): void {
    this.value.update((f) => ({ ...f, minReviewScore: checked ? score : null }));
  }

  protected toggleAmenity(type: TStayAmenity, checked: boolean): void {
    this.value.update((f) => ({ ...f, amenities: checked ? [...f.amenities, type] : f.amenities.filter((a) => a !== type) }));
  }

  protected reset(): void {
    this.value.set(NO_STAY_FILTER);
  }
}
