import { Component, computed, input, model } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroInformationCircle } from '@ng-icons/heroicons/outline';
import { MoneyPipe } from '@core/money/money.pipe';
import { sellingTotal } from '@core/money/pricing';
import { IFlightOffer } from '@shared/models/flight.model';
import { FLIGHT_SORTS, offerMinutes, TFlightSort } from './flight-sort.util';

interface ISortTab {
  readonly sort: TFlightSort;
  /** Première offre de ce tri : son prix et sa durée s'affichent sous le libellé. */
  readonly top: IFlightOffer | undefined;
}

/**
 * Onglets de tri au-dessus des résultats : le moins cher, le meilleur choix, le plus rapide, chacun avec le prix et
 * la durée de son offre en tête. Les offres reçues sont déjà triées pour chaque onglet par la page.
 */
@Component({
  selector: 'bip-flight-sort',
  imports: [TranslocoDirective, NgIcon, MoneyPipe],
  providers: [provideIcons({ heroInformationCircle })],
  host: { class: 'block' },
  template: `
    <div *transloco="let t" [attr.aria-label]="t('search.sort.label')" class="rounded-card bg-surface-card border-border-subtle flex overflow-hidden border" role="tablist">
      @for (tab of tabs(); track tab.sort; let last = $last) {
        <button (click)="value.set(tab.sort)"
                [attr.aria-selected]="value() === tab.sort"
                [class.text-ink]="value() === tab.sort"
                [class.text-muted]="value() !== tab.sort"
                [class.border-r]="!last"
                class="border-border-subtle relative flex min-w-0 flex-1 flex-col items-start gap-0.5 px-3 py-3 text-left transition-colors hover:bg-brand-soft/40 bip-focusable md:px-5"
                role="tab"
                type="button">
          <span class="flex items-center gap-1.5 text-sm font-bold md:text-base">
            {{ t('search.sort.' + tab.sort) }}
            @if (tab.sort === 'best') {
              <ng-icon [attr.title]="t('search.sort.best-info')" aria-hidden="true" name="heroInformationCircle" size="1.1rem"/>
              <span class="sr-only">{{ t('search.sort.best-info') }}</span>
            }
          </span>
          @if (tab.top; as top) {
            <span class="truncate text-xs md:text-sm">{{ price(top) | bipMoney: top.total_currency }} • {{ t('search.sort.duration', duration(top)) }}</span>
          }
          @if (value() === tab.sort) {
            <span aria-hidden="true" class="bg-brand-primary absolute inset-x-3 bottom-0 h-1 rounded-t md:inset-x-5"></span>
          }
        </button>
      }
    </div>
  `,
})
export class FlightSort {
  /** Offres affichées, déjà triées pour chaque onglet. */
  readonly sorted = input.required<Readonly<Record<TFlightSort, readonly IFlightOffer[]>>>();
  readonly value = model<TFlightSort>('best');

  protected readonly tabs = computed((): ISortTab[] => FLIGHT_SORTS.map((sort) => ({ sort, top: this.sorted()[sort][0] })));

  protected price(offer: IFlightOffer): number {
    return sellingTotal(offer);
  }

  protected duration(offer: IFlightOffer): { hours: number; minutes: string } {
    const total = offerMinutes(offer);
    return { hours: Math.floor(total / 60), minutes: String(total % 60).padStart(2, '0') };
  }
}
