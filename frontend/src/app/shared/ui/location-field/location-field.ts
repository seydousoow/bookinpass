import {
  booleanAttribute,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { Airport, CityGroup, PlaceSelection } from '@shared/models/place.model';
import { createAirportIndex, searchAirports } from './airport-search';
import { Spinner } from '../spinner/spinner';

/**
 * Champ de départ / destination.
 *
 * Bâti sur le combobox éditable de `@angular/aria` : l'input porte `ngCombobox`,
 * la liste porte `ngListbox`. Toute la mécanique clavier (↑ ↓, Entrée, Échap,
 * `aria-activedescendant`, `aria-expanded`) vient de la librairie.
 *
 * Les résultats sont regroupés par ville, puis par aéroport (nom + code). Une
 * ville multi-aéroports propose « tous les aéroports » — c'est le cas Dakar
 * (DKR/DSS) et Paris (CDG/ORY).
 *
 * Deux modes de données :
 *  - local : `airports` fourni, filtrage dans le composant ;
 *  - distant : écouter `queryChange` (débouncé) et réalimenter `airports`.
 */
@Component({
  selector: 'bip-location-field',
  imports: [Spinner, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option],
  host: { class: 'block' },
  template: `
    <div class="relative">
      <label [for]="inputId()" class="text-label text-muted mb-1 block font-semibold">
        {{ label() }}
      </label>

      <div [class]="wrapperClass()">
        <svg class="text-muted h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fill-rule="evenodd"
            d="M10 2a5 5 0 0 0-5 5c0 3.5 5 11 5 11s5-7.5 5-11a5 5 0 0 0-5-5Zm0 7a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z"
            clip-rule="evenodd" />
        </svg>

        <input
          ngCombobox
          #combobox="ngCombobox"
          [id]="inputId()"
          type="text"
          autocomplete="off"
          class="text-small text-ink placeholder:text-muted/70 min-w-0 flex-1 bg-transparent focus:outline-none"
          [placeholder]="placeholder()"
          [value]="query()"
          (valueChange)="onType($event)"
          [(expanded)]="expanded"
          [disabled]="disabled()"
          [attr.aria-invalid]="!!error()" />

        @if (loading()) {
          <bip-spinner size="xs" class="text-brand-primary" />
        } @else if (query()) {
          <button
            type="button"
            class="text-muted hover:text-ink bip-focusable rounded-pill p-1"
            (click)="reset()">
            <span class="sr-only">Effacer</span>
            <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path
                d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
            </svg>
          </button>
        }
      </div>

      <ng-template ngComboboxPopup [combobox]="combobox" popupType="listbox">
        <div
          class="rounded-panel border-border-subtle bg-surface-card shadow-panel absolute top-full left-0 z-30 mt-1 w-full min-w-72 overflow-hidden">
          <ul
            ngComboboxWidget
            ngListbox
            #listbox="ngListbox"
            selectionMode="explicit"
            [value]="selectedCodes()"
            (valueChange)="onPick($event)"
            [activeDescendant]="listbox.activeDescendant()"
            class="max-h-80 overflow-y-auto py-1 focus:outline-none">
            @for (group of groups(); track group.cityCode) {
              <div role="group" [attr.aria-label]="group.cityName + ', ' + group.countryName">
                <p
                  class="text-label text-muted bg-surface-muted/70 sticky top-0 flex items-center justify-between px-3 py-1.5 font-semibold">
                  <span class="truncate uppercase">{{ group.cityName }}</span>
                  <span class="truncate">{{ group.countryName }}</span>
                </p>

                @if (group.airports.length > 1) {
                  <li
                    ngOption
                    #cityOpt="ngOption"
                    [value]="'city:' + group.cityCode"
                    [label]="'Tous les aéroports ' + group.cityName"
                    [class]="rowClass(cityOpt.active())">
                    <span class="text-small min-w-0 flex-1 truncate font-semibold">
                      Tous les aéroports · {{ group.cityName }}
                    </span>
                    <span class="text-label bg-brand-soft text-brand-dark rounded-pill px-2 py-0.5 font-mono font-semibold">
                      {{ group.cityCode }}
                    </span>
                  </li>
                }

                @for (airport of group.airports; track airport.iata) {
                  <li
                    ngOption
                    #airportOpt="ngOption"
                    [value]="'airport:' + airport.iata"
                    [label]="airport.name + ' ' + airport.iata"
                    [class]="rowClass(airportOpt.active())">
                    <span class="min-w-0 flex-1">
                      <span class="text-small text-muted block truncate">{{ airport.name }}</span>
                      <span class="text-label text-muted block truncate">
                        {{ airport.cityName }}, {{ airport.countryName }}
                      </span>
                    </span>
                    <span class="text-label border-border-subtle text-muted rounded-pill border px-2 py-0.5 font-mono font-semibold">
                      {{ airport.iata }}
                    </span>
                  </li>
                }
              </div>
            } @empty {
              <li class="text-small text-muted px-3 py-4 text-center">
                @if (query().length < minLength()) {
                  Saisissez au moins {{ minLength() }} caractères
                } @else {
                  Aucune correspondance pour « {{ query() }} »
                }
              </li>
            }
          </ul>
        </div>
      </ng-template>
    </div>

    @if (error()) {
      <p class="text-label text-state-error mt-1.5">{{ error() }}</p>
    }
  `,
})
export class LocationField {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly placeholder = input('Ville, aéroport ou code');
  readonly airports = input<readonly Airport[]>([]);
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly error = input<string>();
  readonly minLength = input(2);
  readonly maxGroups = input(8);
  /** Plafond appliqué à la recherche avant regroupement. */
  readonly maxResults = input(60);
  readonly debounceMs = input(250);

  readonly selection = model<PlaceSelection | null>(null);
  readonly queryChange = output<string>();
  readonly selected = output<PlaceSelection>();

  protected readonly expanded = signal(false);
  protected readonly query = signal('');

  private readonly typed = new Subject<string>();
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.typed
      .pipe(
        debounceTime(this.debounceMs()),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((value) => this.queryChange.emit(value));

    // La sélection posée par le parent (retour arrière, URL) réécrit le texte.
    effect(() => {
      const current = this.selection();
      if (current) {
        this.query.set(`${current.label} (${current.code})`);
      }
    });
  }

  /** La valeur du listbox : le code de la sélection courante, préfixé par son type. */
  protected readonly selectedCodes = computed(() => {
    const current = this.selection();
    return current ? [`${current.kind}:${current.code}`] : [];
  });

  /** Index Fuse.js, reconstruit uniquement quand le jeu de données change. */
  private readonly index = computed(() => createAirportIndex(this.airports()));

  /**
   * Regroupement ville → aéroports, appliqué APRÈS la recherche : l'ordre des
   * groupes suit donc le classement par pertinence de Fuse.
   */
  protected readonly groups = computed<readonly CityGroup[]>(() => {
    const q = this.query().trim();
    const matches =
      q.length < this.minLength()
        ? this.airports().slice(0, this.maxResults())
        : searchAirports(this.index(), q, this.maxResults());

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

  protected wrapperClass(): string {
    const base = [
      'flex min-h-11 items-center gap-2 rounded-field border bg-surface-card px-3 py-2 transition-colors',
      'focus-within:ring-2 focus-within:ring-brand-primary focus-within:ring-offset-2',
    ];
    base.push(
      this.error()
        ? 'border-state-error'
        : this.expanded()
          ? 'border-brand-primary'
          : 'border-border-strong',
    );
    if (this.disabled()) {
      base.push('bg-surface-muted cursor-not-allowed opacity-60');
    }
    return base.join(' ');
  }

  protected rowClass(active: boolean): string {
    return [
      'flex min-h-11 cursor-pointer items-center gap-2 px-3 py-2',
      active ? 'bg-brand-soft' : 'hover:bg-brand-soft/60',
    ].join(' ');
  }

  protected onType(value: string): void {
    this.query.set(value);
    this.typed.next(value);
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
      const airport = this.airports().find((a) => a.iata === code);
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
    this.selection.set(next);
    this.query.set(`${next.label} (${next.code})`);
    this.selected.emit(next);
    this.expanded.set(false);
  }

  protected reset(): void {
    this.query.set('');
    this.selection.set(null);
    this.typed.next('');
  }
}
