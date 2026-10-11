// noinspection ES6PreferShortImport

import { booleanAttribute, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPinSolid, heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';

import { Spinner } from '../spinner/spinner';
import { IAirport, ICityGroup, IPlaceSelection } from '../../models/place.model';
import { createAirportIndex, searchAirports } from './airport-search';
import { AirportStore } from './airport.store';

const SEARCH_MIN_LENGTH = 2;
const MAX_RESULTS = 180;

@Component({
  selector: 'bip-location-field',
  imports: [Spinner, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, NgIcon, TranslocoDirective, TranslocoPipe],
  host: { class: 'block' },
  templateUrl: './location-field.html',
  providers: [provideIcons({ heroMapPinSolid, heroXMarkSolid })],
})
export class LocationField implements FormValueControl<string | null> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, {
    transform: booleanAttribute,
  });
  readonly placeholder = input('search.location.placeholder');

  readonly minLength = input<number>();
  readonly maxGroups = input(8);

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);

  readonly touch = output<void>();
  readonly value = model<string | null>(null);

  protected readonly selected = signal<IPlaceSelection | undefined>(undefined);

  protected readonly opened = model(false);
  protected query = model('');

  protected readonly selectedCodes = computed(() => {
    const selection = this.selected();
    return selection ? [`${selection.kind}:${selection.code}`] : [];
  });
  private readonly airportStore = inject(AirportStore);
  protected readonly loading = this.airportStore.loading;
  protected readonly airports = this.airportStore.airports;
  /**
   * Fuse index is rebuilt only when the airport dataset changes.
   */
  private readonly airportIndex = computed(() => createAirportIndex(this.airports()));
  /**
   * Search and group airports by city.
   *
   * Search ranking is preserved because grouping happens after
   * the Fuse results have been returned.
   */
  protected readonly groups = computed<readonly ICityGroup[]>(() => {
    const query = this.query().trim();
    const airports =
      query.length < SEARCH_MIN_LENGTH ? this.airports().slice(0, MAX_RESULTS) : searchAirports(this.airportIndex(), query, MAX_RESULTS);
    const groups = new Map<string, IAirport[]>();
    for (const airport of airports) {
      const airportsInCity = groups.get(airport.cityCode);
      if (airportsInCity) {
        airportsInCity.push(airport);
      } else {
        groups.set(airport.cityCode, [airport]);
      }
    }

    return [...groups.entries()].slice(0, this.maxGroups()).map(([cityCode, cityAirports]) => {
      const { cityName, countryCode, countryName } = cityAirports[0];
      return { cityCode, cityName: cityName, countryName: countryName, countryCode: countryCode, airports: cityAirports };
    });
  });

  constructor() {
    effect(() => {
      const value = this.value();
      const selected = this.selected();
      console.log(value, selected)
      if (value && (!selected || !Object.is(value, selected.code))) {
        const selection = this.findAirportSelection(value) ?? this.findCitySelection(value);
        this.selected.set(selection);
        if (selection) {
          this.query.set(`${selection.label} (${selection.code})`);
        }
      }
    });
  }

  clear(): void {
    this.query.set('');
    this.value.set(null);
  }

  reset(): void {
    // Nothing to reset.
  }

  protected wrapperClass(): string {
    const classes = [
      'flex min-h-11 items-center gap-2 rounded-field border bg-surface-card px-3 py-2 transition-colors focus-within:ring-2 focus-within:ring-brand-primary focus-within:ring-offset-2',
    ];

    if (this.touched() && this.invalid()) {
      classes.push('border-state-error');
    } else if (this.opened()) {
      classes.push('border-brand-primary');
    } else {
      classes.push('border-border-strong', 'hover:border-brand-primary/60');
    }

    if (this.disabled()) {
      classes.push('cursor-not-allowed', 'bg-surface-muted', 'opacity-60');
    }

    return classes.join(' ');
  }

  protected rowClass(active: boolean): string {
    return ['flex min-h-11 w-full items-center justify-between gap-2 px-3 py-2', active ? 'bg-brand-soft' : 'hover:bg-brand-soft/60'].join(' ');
  }

  /**
   * Reconstructs the domain selection from the listbox value.
   */
  protected onPick(codes: string[]): void {
    const code = codes[0];
    if (!code) {
      return;
    }

    const [kind, value] = code.split(':');
    if (!value) {
      return;
    }

    const selection = kind === 'city' ? this.findCitySelection(value) : kind === 'airport' ? this.findAirportSelection(value) : undefined;
    if (!selection) {
      return;
    }

    this.selected.set(selection);
    this.value.set(selection.code);
    this.query.set(`${selection.label} (${selection.code})`);
    this.opened.set(false);
  }

  private findCitySelection(cityCode: string): IPlaceSelection | undefined {
    const city = this.groups().find((group) => group.cityCode === cityCode);

    if (!city) {
      return undefined;
    }
    return {
      kind: 'city',
      code: city.cityCode,
      label: city.cityName,
      subLabel: `Tous les aéroports · ${city.countryName}`,
      countryCode: city.countryCode,
    };
  }

  private findAirportSelection(iata: string): IPlaceSelection | undefined {
    const airport = this.airports().find(({ iata: x }) => x === iata);

    if (!airport) {
      return undefined;
    }

    return {
      kind: 'airport',
      code: airport.iata,
      label: airport.name,
      subLabel: `${airport.cityName}, ${airport.countryName}`,
      countryCode: airport.countryCode,
    };
  }
}
