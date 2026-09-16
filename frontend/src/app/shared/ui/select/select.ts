// noinspection ES6PreferShortImport

import { afterRenderEffect, booleanAttribute, Component, computed, input, model, output, signal, viewChild } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { SelectOption } from '../../models/select.model';
import { Field } from '../field/field';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { CdkConnectedOverlay } from '@angular/cdk/overlay';
import { translateSignal, TranslocoPipe } from '@jsverse/transloco';
import { heroCheckSolid, heroChevronDownSolid } from '@ng-icons/heroicons/solid';
import { NgIcon, provideIcons } from '@ng-icons/core';

@Component({
  selector: 'bip-select',
  imports: [Field, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, CdkConnectedOverlay, TranslocoPipe, NgIcon],
  host: { class: 'block' },
  templateUrl: './select.html',
  providers: [provideIcons({ heroCheckSolid, heroChevronDownSolid })],
})
export class Select<T = string> implements FormValueControl<T[]> {
  readonly options = input.required<readonly SelectOption<T>[]>();
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });

  readonly placeholder = input(translateSignal('common.select')());
  readonly hint = input<string>();
  readonly multiple = input(false, { transform: booleanAttribute });
  public open = signal(false);

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly showErrors = computed(() => this.touched() || this.invalid());
  readonly touch = output<void>();

  readonly value = model<T[]>([]);

  readonly displayValue = computed(() =>
    this.options()
      .filter((o) => this.value().includes(o.value))
      .map((o) => o.label)
      .join(', '),
  );

  private readonly listBox = viewChild(Listbox);

  constructor() {
    afterRenderEffect(() => {
      this.listBox()?.scrollActiveItemIntoView();
    });
  }

  protected triggerClass(): string {
    const base = [
      'flex min-h-11 w-full items-center gap-2 rounded-field border bg-surface-card px-3 py-2 text-left transition-colors bip-focusable',
    ];
    if (this.touched() && this.invalid()) {
      base.push('border-state-error');
    } else {
      base.push(this.open() ? 'border-brand-primary' : 'border-border-strong hover:border-brand-primary/60');
    }
    base.push(this.disabled() ? 'bg-surface-muted cursor-not-allowed opacity-60' : 'cursor-pointer');
    return base.join(' ');
  }

  protected optionClass(active: boolean): string {
    const base = ['flex min-h-11 items-center gap-2 px-3 py-2.5 text-sm'];
    base.push(active ? 'bg-brand-soft text-brand-dark' : 'hover:bg-brand-soft/60');
    return base.join(' ');
  }

  protected onCommit(): void {
    if (!this.multiple()) {
      this.open.set(false);
    }
  }
}
