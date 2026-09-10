// noinspection ES6PreferShortImport

import { booleanAttribute, Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { createAirportIndex, searchAirports } from './airport-search';
import { Spinner } from '../spinner/spinner';
import { Airport, CityGroup, PlaceSelection } from '../../models/place.model';
import { AirportStore } from './airport.store';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPinSolid, heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'bip-location-field',
  imports: [Spinner, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, NgIcon, TranslocoDirective, TranslocoPipe],
  host: { class: 'block' },
  templateUrl: './location-field.html',
  providers: [provideIcons({ heroMapPinSolid, heroXMarkSolid })],
})
export class LocationField implements FormValueControl<string | null> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly placeholder = input('search.location.placeholder');

  readonly minLength = input<number>();
  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly touch = output<void>();
  readonly value = model<string | null>(null);
  readonly selected = signal<PlaceSelection | undefined>(undefined);

  readonly maxGroups = input(8);

  protected open = model(false);
  protected query = model('');
  /** La valeur du listbox : le code de la sélection courante, préfixé par son type. */
  protected readonly selectedCodes = computed(() => {
    const current = this.selected();
    return current ? [`${current.kind}:${current.code}`] : [];
  });

  private readonly airportStore = inject(AirportStore);
  /** Index Fuse.js, reconstruit uniquement quand le jeu de données change. */
  private readonly index = computed(() => createAirportIndex(this.airports));
  /**
   * Regroupement ville → aéroports, appliqué APRÈS la recherche : l'ordre des
   * groupes suit donc le classement par pertinence de Fuse.
   */
  protected readonly groups = computed<readonly CityGroup[]>(() => {
    const q = this.query().trim();
    const matches = q.length < 2 ? this.airports.slice(0, 60) : searchAirports(this.index(), q, 60);

    const byCity = new Map<string, Airport[]>();
    for (const airport of matches) {
      const bucket = byCity.get(airport.cityCode);
      if (bucket) {
        bucket.push(airport);
      } else {
        byCity.set(airport.cityCode, [airport]);
      }
    }

    return [...byCity.entries()].slice(0, this.maxGroups()).map(([cityCode, list]) => ({
      cityCode,
      cityName: list[0].cityName,
      countryName: list[0].countryName,
      countryCode: list[0].countryCode,
      airports: list,
    }));
  });

  get loading(): boolean {
    return this.airportStore.loading();
  }

  get airports(): readonly Airport[] {
    return this.airportStore.airports();
  }

  reset(): void {
    /* empty */
  }

  clear(): void {
    this.query.set('');
    this.selected.set(undefined);
    this.reset();
  }

  protected wrapperClass(): string {
    const base = [
      'flex min-h-11 items-center gap-2 rounded-field border bg-surface-card px-3 py-2 transition-colors focus-within:ring-2 focus-within:ring-brand-primary focus-within:ring-offset-2',
    ];
    if (this.touched() && !this.invalid()) {
      base.push('border-state-error');
    } else {
      base.push(this.open() ? 'border-brand-primary' : 'border-border-strong  hover:border-brand-primary/60');
    }
    // base.push(this.disabled() ? 'bg-surface-muted cursor-not-allowed opacity-60' : 'cursor-pointer');
    return base.join(' ');
  }

  protected rowClass(active: boolean): string {
    const base = ['flex min-h-11 items-center gap-2 px-3 py-2 w-full justify-between items-center'];
    base.push(active ? 'bg-brand-soft' : 'hover:bg-brand-soft/60');
    return base.join(' ');
  }

  /** Reconstruit une sélection métier à partir du code choisi dans le listbox. */
  protected onPick(codes: string[]): void {
    const picked = codes[0];
    if (!picked) {
      return;
    }
    const [kind, code] = picked.split(':');

    let next: PlaceSelection | null = null;
    if (kind === 'city') {
      const group = this.groups().find((g) => g.cityCode === code);
      if (group) {
        next = {
          kind: 'city',
          code: group.cityCode,
          label: group.cityName,
          subLabel: `Tous les aéroports · ${group.countryName}`,
          countryCode: group.countryCode,
        };
      }
    } else {
      const airport = this.airports.find((a) => a.iata === code);
      if (airport) {
        next = {
          kind: 'airport',
          code: airport.iata,
          label: airport.name,
          subLabel: `${airport.cityName}, ${airport.countryName}`,
          countryCode: airport.countryCode,
        };
      }
    }

    if (!next) {
      return;
    }
    this.selected.set(next);
    this.value.set(next.code);
    this.query.set(`${next.label} (${next.code})`);
    this.open.set(false);
  }
}
