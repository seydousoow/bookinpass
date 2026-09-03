import {
  booleanAttribute,
  Component,
  computed,
  forwardRef,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { SelectOption } from '@shared/models/select.model';
import { Field } from '../field/field';

/**
 * Sélecteur bâti sur `@angular/aria` : `ngCombobox` pour le déclencheur non
 * éditable, `ngListbox` / `ngOption` pour la liste. Les rôles, l'état
 * `aria-expanded`, `aria-activedescendant`, le focus roving, la recherche par
 * frappe et la navigation clavier viennent de la librairie — on n'écrit ici que
 * la présentation.
 */
@Component({
  selector: 'bip-select',
  imports: [Field, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option],
  host: { class: 'block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Select), multi: true },
  ],
  template: `
    <bip-field
      [label]="label()"
      [for]="inputId()"
      [hint]="hint()"
      [error]="error()"
      [required]="required()">
      <div class="relative">
        <div
          ngCombobox
          #combobox="ngCombobox"
          [id]="inputId()"
          [(expanded)]="expanded"
          [disabled]="disabled()"
          [class]="triggerClass()">
          <span class="text-small min-w-0 flex-1 truncate" [class.text-muted]="!selectedLabel()">
            {{ selectedLabel() || placeholder() }}
          </span>
          <svg
            class="text-muted h-4 w-4 shrink-0 transition-transform"
            [class.rotate-180]="expanded()"
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true">
            <path
              fill-rule="evenodd"
              d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
              clip-rule="evenodd" />
          </svg>
        </div>

        <ng-template ngComboboxPopup [combobox]="combobox" popupType="listbox">
          <div
            class="rounded-panel border-border-subtle bg-surface-card shadow-panel absolute top-full left-0 z-30 mt-1 w-full overflow-hidden">
            <ul
              ngComboboxWidget
              ngListbox
              #listbox="ngListbox"
              selectionMode="explicit"
              [multi]="multiple()"
              [value]="value()"
              (valueChange)="onListboxChange($event)"
              [activeDescendant]="listbox.activeDescendant()"
              class="max-h-64 overflow-y-auto py-1 focus:outline-none">
              @for (option of options(); track option.value) {
                <li
                  ngOption
                  [value]="option.value"
                  [label]="option.label"
                  [disabled]="!!option.disabled"
                  #opt="ngOption"
                  [class]="optionClass(opt.active(), !!option.disabled)">
                  <span class="min-w-0 flex-1">
                    <span class="block truncate" [class.font-semibold]="opt.selected()">
                      {{ option.label }}
                    </span>
                    @if (option.hint) {
                      <span class="text-label text-muted block truncate">{{ option.hint }}</span>
                    }
                  </span>
                  @if (opt.selected()) {
                    <svg class="text-brand-primary h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                      <path
                        fill-rule="evenodd"
                        d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.79 6.8-6.8a1 1 0 0 1 1.4 0Z"
                        clip-rule="evenodd" />
                    </svg>
                  }
                </li>
              } @empty {
                <li class="text-small text-muted px-3 py-3">Aucun élément</li>
              }
            </ul>
          </div>
        </ng-template>
      </div>
    </bip-field>
  `,
})
export class Select<T = string> implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly options = input.required<readonly SelectOption<T>[]>();
  readonly label = input<string>();
  readonly placeholder = input('Sélectionner');
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly required = input(false, { transform: booleanAttribute });
  readonly multiple = input(false, { transform: booleanAttribute });

  readonly disabled = model(false);
  readonly value = model<T[]>([]);
  readonly changed = output<readonly T[]>();

  protected readonly expanded = signal(false);

  protected readonly selectedLabel = computed(() =>
    this.options()
      .filter((o) => this.value().includes(o.value))
      .map((o) => o.label)
      .join(', '),
  );

  protected triggerClass(): string {
    const base = [
      'flex min-h-11 w-full cursor-pointer items-center gap-2 rounded-field border bg-surface-card',
      'px-3 py-2 text-left transition-colors bip-focusable',
    ];
    base.push(
      this.error()
        ? 'border-state-error'
        : this.expanded()
          ? 'border-brand-primary'
          : 'border-border-strong hover:border-brand-primary/60',
    );
    if (this.disabled()) {
      base.push('cursor-not-allowed opacity-60');
    }
    return base.join(' ');
  }

  protected optionClass(active: boolean, optionDisabled: boolean): string {
    const base = [
      'flex min-h-11 cursor-pointer items-center gap-2 px-3 py-2.5 text-small',
      active ? 'bg-brand-soft text-brand-dark' : 'hover:bg-brand-soft/60',
    ];
    if (optionDisabled) {
      base.push('cursor-not-allowed opacity-50');
    }
    return base.join(' ');
  }

  protected onListboxChange(next: T[]): void {
    this.value.set(next);
    this.onChangeFn(next);
    this.onTouchedFn();
    this.changed.emit(next);
    if (!this.multiple()) {
      this.expanded.set(false);
    }
  }

  private onChangeFn: (value: T[]) => void = () => undefined;
  private onTouchedFn: () => void = () => undefined;

  writeValue(value: T[] | T | null): void {
    if (value === null || value === undefined) {
      this.value.set([]);
    } else {
      this.value.set(Array.isArray(value) ? value : [value]);
    }
  }

  registerOnChange(fn: (value: T[]) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
