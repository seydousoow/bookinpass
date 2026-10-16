import { Component, computed, input, model } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroInformationCircle } from '@ng-icons/heroicons/outline';
import { MoneyPipe } from '@core/money/money.pipe';
import { sellingTotal } from '@core/money/pricing';
import { IFlightOffer } from '@shared/models/flight.model';
import { BEST_WEIGHTS, FLIGHT_SORTS, offerMinutes, TFlightSort } from './flight-sort.util';

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
    <div *transloco="let t" [attr.aria-label]="t('search.sort.label')" class="rounded-card bg-surface-card border-border-subtle flex border" role="tablist">
      @for (tab of tabs(); track tab.sort; let last = $last) {
        <!-- Pas d'overflow-hidden sur la barre : il couperait l'infobulle ; les coins sont arrondis onglet par onglet. -->
        <button (click)="value.set(tab.sort)"
                [attr.aria-describedby]="tab.sort === 'best' ? 'flight-sort-best-info' : null"
                [attr.aria-selected]="value() === tab.sort"
                [class.text-ink]="value() === tab.sort"
                [class.text-muted]="value() !== tab.sort"
                [class.border-r]="!last"
                class="group/tab border-border-subtle first:rounded-l-card last:rounded-r-card relative flex min-w-0 flex-1 flex-col items-start gap-0.5 px-3 py-3 text-left transition-colors hover:bg-brand-soft/40 bip-focusable md:px-5"
                role="tab"
                type="button">
          <span class="flex items-center gap-1.5 text-sm font-bold md:text-base">
            {{ t('search.sort.' + tab.sort) }}
            @if (tab.sort === 'best') {
              <!-- Infobulle : au survol de l'icône, ou au focus clavier de l'onglet ; lue via aria-describedby. -->
              <span class="group/info relative flex">
                <ng-icon aria-hidden="true" class="cursor-help" name="heroInformationCircle" size="1.1rem"/>
                <span class="bg-ink text-surface-card pointer-events-none invisible absolute top-full left-1/2 z-30 mt-2 w-72 max-w-[calc(100vw-2rem)] -translate-x-1/2 rounded-lg p-3 text-left text-xs font-normal leading-relaxed opacity-0 shadow-panel transition-opacity group-hover/info:visible group-hover/info:opacity-100 group-focus-visible/tab:visible group-focus-visible/tab:opacity-100"
                      id="flight-sort-best-info"
                      role="tooltip">
                  <span class="block">{{ t('search.sort.best-info') }}</span>
                  <span class="mt-2 block font-semibold">{{ t('search.sort.how.title') }}</span>
                  <span class="mt-1 block">
                    @for (criterion of criteria; track criterion.key) {
                      <span class="flex justify-between gap-3">
                        <span>{{ t('search.sort.how.' + criterion.key) }}</span>
                        <span class="font-semibold tabular-nums">{{ criterion.percent }} %</span>
                      </span>
                    }
                  </span>
                  <span class="mt-2 block opacity-80">{{ t('search.sort.how.scale') }}</span>
                </span>
              </span>
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

  /** Critères du « meilleur choix » et leur poids, tirés de `BEST_WEIGHTS` : l'infobulle dit ce que fait le tri. */
  protected readonly criteria = (Object.keys(BEST_WEIGHTS) as (keyof typeof BEST_WEIGHTS)[]).map((key) => ({
    key,
    percent: Math.round(BEST_WEIGHTS[key] * 100),
  }));

  protected readonly tabs = computed((): ISortTab[] => FLIGHT_SORTS.map((sort) => ({ sort, top: this.sorted()[sort][0] })));

  protected price(offer: IFlightOffer): number {
    return sellingTotal(offer);
  }

  protected duration(offer: IFlightOffer): { hours: number; minutes: string } {
    const total = offerMinutes(offer);
    return { hours: Math.floor(total / 60), minutes: String(total % 60).padStart(2, '0') };
  }
}
