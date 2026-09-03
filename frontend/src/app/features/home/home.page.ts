import { Component, inject, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { AirportStore } from '@core/places/airport.store';
import { PlaceSelection } from '@shared/models/place.model';
import { SelectOption } from '@shared/models/select.model';
import {
  Button,
  Card,
  DatePicker,
  DateRangeValue,
  LocationField,
  PassengerCount,
  PeopleSelector,
  RadioGroup,
  Select,
} from '@shared/ui';

/**
 * Page d'accueil : le moteur de recherche vol assemblé à partir du socle.
 * Rien n'est appelé côté API pour l'instant — la soumission affiche seulement
 * les critères, le temps que le contrat existe.
 */
@Component({
  selector: 'bip-home-page',
  imports: [
    TranslocoDirective,
    LocationField,
    DatePicker,
    PeopleSelector,
    RadioGroup,
    Select,
    Button,
    Card,
  ],
  template: `
    <ng-container *transloco="let t">
      <section class="from-brand-dark to-brand-primary bg-gradient-to-br">
        <div class="mx-auto max-w-7xl px-4 py-10 sm:py-16">
          <h1 class="text-display text-onbrand max-w-3xl">{{ t('home.title') }}</h1>
          <p class="text-body text-brand-soft mt-3 max-w-2xl">{{ t('home.subtitle') }}</p>

          <bip-card padding="md" class="mt-8">
            <bip-radio-group
              groupName="trip-type"
              appearance="segmented"
              [options]="tripTypes"
              [(value)]="tripType" />

            <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <bip-location-field
                inputId="from"
                [label]="t('search.from')"
                [airports]="airports.airports()"
                [loading]="airports.loading()"
                [(selection)]="origin" />

              <bip-location-field
                inputId="to"
                [label]="t('search.to')"
                [airports]="airports.airports()"
                [loading]="airports.loading()"
                [(selection)]="destination"
                [error]="sameCityError()" />

              <bip-date-picker
                [label]="tripType() === 'round' ? t('search.depart') + ' — ' + t('search.return') : t('search.depart')"
                [mode]="tripType() === 'round' ? 'range' : 'single'"
                [visibleMonths]="2"
                [(value)]="dates" />

              <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 xl:gap-3">
                <bip-people-selector [label]="t('search.travelers')" [(value)]="passengers" />
              </div>
            </div>

            <div class="flex flex-wrap items-end gap-3">
              <bip-select
                inputId="cabin"
                class="w-full sm:w-56"
                [label]="t('search.cabin')"
                [options]="cabins"
                [(value)]="cabin" />
              <button
                bipButton
                variant="accent"
                size="lg"
                type="button"
                class="ml-auto w-full sm:w-auto"
                [disabled]="!canSearch()"
                (click)="submit()">
                {{ t('search.submit') }}
              </button>
            </div>
          </bip-card>

          @if (lastQuery(); as query) {
            <pre
              class="text-label text-brand-soft bg-ink/30 rounded-card mt-4 overflow-x-auto p-3">{{ query }}</pre>
          }
        </div>
      </section>
    </ng-container>
  `,
})
export class HomePage {
  protected readonly airports = inject(AirportStore);

  protected readonly tripType = signal<'round' | 'oneway'>('round');
  protected readonly origin = signal<PlaceSelection | null>(null);
  protected readonly destination = signal<PlaceSelection | null>(null);
  protected readonly dates = signal<DateRangeValue>({ start: null, end: null });
  protected readonly passengers = signal<PassengerCount>({ adults: 1, children: 0, infants: 0 });
  protected readonly cabin = signal<string[]>(['economy']);
  protected readonly lastQuery = signal<string | null>(null);

  protected readonly tripTypes: readonly SelectOption<'round' | 'oneway'>[] = [
    { label: 'Aller-retour', value: 'round' },
    { label: 'Aller simple', value: 'oneway' },
  ];

  protected readonly cabins: readonly SelectOption[] = [
    { label: 'Économique', value: 'economy' },
    { label: 'Premium', value: 'premium' },
    { label: 'Affaires', value: 'business' },
    { label: 'Première', value: 'first' },
  ];

  protected sameCityError(): string | undefined {
    const from = this.origin();
    const to = this.destination();
    return from && to && from.code === to.code
      ? 'Le départ et la destination doivent être différents.'
      : undefined;
  }

  protected canSearch(): boolean {
    const hasPlaces = !!this.origin() && !!this.destination() && !this.sameCityError();
    const hasDates =
      this.tripType() === 'round'
        ? !!this.dates().start && !!this.dates().end
        : !!this.dates().start;
    return hasPlaces && hasDates;
  }

  protected submit(): void {
    this.lastQuery.set(
      JSON.stringify(
        {
          tripType: this.tripType(),
          from: this.origin()?.code,
          to: this.destination()?.code,
          dates: this.dates(),
          passengers: this.passengers(),
          cabin: this.cabin()[0],
        },
        null,
        2,
      ),
    );
  }
}
