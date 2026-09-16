import { Component, computed, input, model, signal } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { CurrencyOption } from '@shared/models/money.model';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheckSolid, heroChevronDownSolid } from '@ng-icons/heroicons/solid';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'bip-currency-selector',
  imports: [Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, NgIcon, TranslocoPipe],
  host: { class: 'block' },
  providers: [provideIcons({ heroChevronDownSolid, heroCheckSolid })],
  templateUrl: './currency-selector.html',
})
export class CurrencySelector {
  readonly currencies = input.required<readonly CurrencyOption[]>();
  readonly selected = model.required<string>();

  readonly opened = signal(false);
  readonly value = computed(() => [this.selected()]);

  onPick(codes: string[]): void {
    if (codes.length !== 1 || Object.is(codes[0], this.value())) {
      return;
    }
    this.selected.set(codes[0]);
    this.opened.set(false);
  }
}
