import { Component, inject, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { AirportStore } from '@core/places/airport.store';
import { PlaceSelection } from '@shared/models/place.model';
import { SelectOption } from '@shared/models/select.model';
import { Button, Card, DatePicker, DateRangeValue, LocationField, PassengerCount, PeopleSelector, RadioGroup, Select } from '@shared/ui';

@Component({
  selector: 'bip-home-page',
  imports: [TranslocoDirective, LocationField, DatePicker, PeopleSelector, RadioGroup, Select, Button, Card],
  templateUrl: './home.html',
})
export class Home {
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
    return from && to && from.code === to.code ? 'Le départ et la destination doivent être différents.' : undefined;
  }

  protected canSearch(): boolean {
    const hasPlaces = !!this.origin() && !!this.destination() && !this.sameCityError();
    const hasDates = this.tripType() === 'round' ? !!this.dates().start && !!this.dates().end : !!this.dates().start;
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
