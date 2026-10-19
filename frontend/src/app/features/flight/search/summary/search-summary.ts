import { Component, computed, effect, inject, input, model } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroArrowRight, heroArrowsRightLeft } from '@ng-icons/heroicons/outline';
import { DatePipe } from '@core/i18n/date.pipe';
import { IFlightSearch } from '@shared/models';
import { Button, Modal, Viewport } from '@shared/ui';
import { AirportStore } from '@shared/ui/location-field/airport.store';
import { FlightSearchForm } from '@features/flight/search-form/flight-search-form';

/**
 * Critères de la recherche en cours (lus de l'URL par la page) et leur modification : formulaire déplié sous le
 * résumé sur grand écran, dans une modale sur mobile. La soumission change l'URL, ce qui referme l'édition.
 */
@Component({
  selector: 'bip-search-summary',
  templateUrl: './search-summary.html',
  imports: [TranslocoDirective, DatePipe, NgIcon, Button, Modal, FlightSearchForm],
  host: { class: 'block rounded-card border border-border-subtle bg-surface-card p-3' },
  providers: provideIcons({ heroArrowRight, heroArrowsRightLeft }),
})
export class SearchSummary {
  readonly search = input.required<IFlightSearch>();
  readonly disabled = input.required<boolean>();
  readonly editing = model(false);

  protected readonly isMobile = inject(Viewport).isMobile;
  protected readonly dateFormat: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' };
  protected readonly travelers = computed(() => {
    const { adults, children, infants } = this.search().passengers;
    return adults + children + infants;
  });

  private readonly airports = inject(AirportStore).airports;

  constructor() {
    // Nouvelle recherche (URL changée) : l'édition est terminée.
    effect(() => {
      this.search();
      this.editing.set(false);
    });
  }

  /** « Dakar » pour DSS ; le code tant que le référentiel charge. */
  protected cityName(code: string): string {
    const airport = this.airports().find((a) => a.iata === code || a.cityCode === code);
    return airport?.cityName ?? code;
  }
}
