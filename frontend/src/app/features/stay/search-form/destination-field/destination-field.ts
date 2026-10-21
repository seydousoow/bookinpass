import { booleanAttribute, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPinSolid, heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, filter, finalize, map, of, switchMap } from 'rxjs';
import { IStayDestination } from '@shared/models/stay.model';
import { Spinner } from '@shared/ui/spinner/spinner';
import { isDestinationId, StayDestinationService } from './stay-destination.service';

const SEARCH_MIN_LENGTH = 2;
const DEBOUNCE_MS = 500;

/**
 * Ville de destination d'un hébergement : autocomplétion côté serveur sur les villes GeoNames.
 * La valeur du champ est l'identifiant GeoNames ; le pays s'affiche dans la langue active.
 */
@Component({
  selector: 'bip-destination-field',
  imports: [Spinner, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, NgIcon, TranslocoDirective, TranslocoPipe],
  host: { class: 'block' },
  templateUrl: './destination-field.html',
  providers: [provideIcons({ heroMapPinSolid, heroXMarkSolid })],
})
export class DestinationField implements FormValueControl<string> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly placeholder = input('stay.search.destination-placeholder');

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);

  readonly touch = output<void>();
  readonly value = model('');

  protected readonly opened = signal(false);
  protected readonly query = signal('');
  protected readonly loading = signal(false);
  protected readonly selected = signal<IStayDestination | undefined>(undefined);
  protected readonly selectedIds = computed(() => {
    const selection = this.selected();
    return selection ? [String(selection.id)] : [];
  });
  /** Texte affiché pour la ville choisie : toute autre saisie vaut nouvelle recherche. */
  private shownLabel = '';
  private readonly api = inject(StayDestinationService);
  /** Suggestions de la saisie en cours ; rien tant qu'elle est trop courte ou qu'elle affiche la ville choisie. */
  protected readonly results = toSignal(
    toObservable(this.query).pipe(
      map((query) => query.trim()),
      debounceTime(DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((query) => {
        if (query.length < SEARCH_MIN_LENGTH || (untracked(this.selected) && query === this.shownLabel)) {
          return of([]);
        }
        this.loading.set(true);
        return this.api.search(query).pipe(
          catchError(() => of([])),
          finalize(() => this.loading.set(false)),
        );
      }),
    ),
    { initialValue: [] as IStayDestination[] },
  );
  private readonly transloco = inject(TranslocoService);
  private readonly countryNames = computed(() => new Intl.DisplayNames([this.transloco.activeLang()], { type: 'region' }));

  constructor() {
    // Valeur posée de l'extérieur (recherche en cours ou mémorisée) : on relit la ville pour l'afficher.
    toObservable(this.value)
      .pipe(
        filter((id) => isDestinationId(id) && String(untracked(this.selected)?.id) !== id),
        switchMap((id) => this.api.find(id).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe((destination) => this.show(destination));

    // Changement de langue : le pays de la ville choisie se relit dans la nouvelle.
    effect(() => {
      this.countryNames();
      const selected = untracked(this.selected);
      if (selected) {
        untracked(() => this.show(selected));
      }
    });

    // Une saisie qui ne correspond plus à la ville choisie l'efface : le champ redevient requis.
    effect(() => {
      const query = this.query();
      if (untracked(this.selected) && query !== this.shownLabel) {
        this.selected.set(undefined);
        this.value.set('');
      }
    });
  }

  protected countryName(code: string): string {
    return this.countryNames().of(code) ?? code;
  }

  protected labelOf(destination: IStayDestination | undefined): string {
    return destination ? `${destination.name}, ${this.countryName(destination.countryCode)}` : '';
  }

  protected clear(): void {
    this.selected.set(undefined);
    this.query.set('');
    this.value.set('');
  }

  protected onPick(ids: string[]): void {
    const destination = this.results().find(({ id }) => String(id) === ids[0]);
    if (!destination) {
      return;
    }
    this.show(destination);
    this.value.set(String(destination.id));
    this.opened.set(false);
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
    return ['flex min-h-11 w-full items-center gap-2 px-3 py-2', active ? 'bg-brand-soft' : 'hover:bg-brand-soft/60'].join(' ');
  }

  private show(destination: IStayDestination): void {
    this.shownLabel = this.labelOf(destination);
    this.selected.set(destination);
    this.query.set(this.shownLabel);
  }
}
