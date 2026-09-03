import { Component, computed, input, model, signal } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { CurrencyOption } from '@shared/models/money.model';

/**
 * Choix de la devise d'affichage. Purement présentationnel : il reçoit la
 * liste et la sélection, il n'interroge rien (plan §2.2 règle 2).
 *
 * Bâti sur `@angular/aria` comme les autres listes : rôles, `aria-expanded`,
 * `aria-activedescendant` et navigation clavier viennent de la librairie.
 */
@Component({
  selector: 'bip-currency-selector',
  imports: [Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option],
  host: { class: 'block' },
  template: `
    <div class="relative">
      <label [for]="inputId()" class="sr-only">{{ label() }}</label>
      <div
        ngCombobox
        #combobox="ngCombobox"
        [id]="inputId()"
        [(expanded)]="expanded"
        [class]="triggerClass()">
        <span class="font-mono font-semibold">{{ selected() }}</span>
        <svg
          class="h-3.5 w-3.5 shrink-0 transition-transform"
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
          class="rounded-panel border-border-subtle bg-surface-card shadow-panel absolute top-full right-0 z-30 mt-1 w-56 overflow-hidden">
          <ul
            ngComboboxWidget
            ngListbox
            #listbox="ngListbox"
            selectionMode="explicit"
            [value]="value()"
            (valueChange)="onPick($event)"
            [activeDescendant]="listbox.activeDescendant()"
            class="max-h-64 overflow-y-auto py-1 focus:outline-none">
            @for (currency of currencies(); track currency.code) {
              <li
                ngOption
                #opt="ngOption"
                [value]="currency.code"
                [label]="currency.code + ' ' + currency.label"
                [class]="optionClass(opt.active())">
                <span class="text-label w-10 shrink-0 font-mono font-semibold">{{ currency.code }}</span>
                <span class="text-small text-ink min-w-0 flex-1 truncate capitalize">{{ currency.label }}</span>
                @if (opt.selected()) {
                  <svg class="text-brand-primary h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path
                      fill-rule="evenodd"
                      d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.79 6.8-6.8a1 1 0 0 1 1.4 0Z"
                      clip-rule="evenodd" />
                  </svg>
                }
              </li>
            }
          </ul>
        </div>
      </ng-template>
    </div>
  `,
})
export class CurrencySelector {
  readonly inputId = input('currency-selector');
  readonly label = input('Devise d’affichage');
  readonly currencies = input.required<readonly CurrencyOption[]>();
  readonly selected = model.required<string>();

  protected readonly expanded = signal(false);
  protected readonly value = computed(() => [this.selected()]);

  protected triggerClass(): string {
    return [
      'flex min-h-8 cursor-pointer items-center gap-1.5 rounded-pill border border-border-subtle',
      'bg-surface-card px-2.5 text-label text-muted uppercase transition-colors',
      'hover:text-brand-dark bip-focusable',
    ].join(' ');
  }

  protected optionClass(active: boolean): string {
    return [
      'flex min-h-11 cursor-pointer items-center gap-2 px-3 py-2',
      active ? 'bg-brand-soft' : 'hover:bg-brand-soft/60',
    ].join(' ');
  }

  protected onPick(codes: string[]): void {
    if (codes[0]) {
      this.selected.set(codes[0]);
      this.expanded.set(false);
    }
  }
}
