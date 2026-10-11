import { booleanAttribute, Component, computed, effect, input, model, output } from '@angular/core';
// noinspection ES6PreferShortImport
import { ISelectOption } from '../../models/select.model';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { translate, TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroExclamationCircleSolid } from '@ng-icons/heroicons/solid';

@Component({
  selector: 'bip-radio-group',
  host: { class: 'block' },
  templateUrl: './radio-group.html',
  imports: [NgIcon, TranslocoDirective],
  providers: [provideIcons({ heroExclamationCircleSolid })],
})
export class RadioGroup<T = string> implements FormValueControl<T | null> {
  readonly options = input.required<readonly ISelectOption<T>[]>();
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly appearance = input<'list' | 'segmented'>('list');

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly value = model<T | null>(null);
  readonly showError = computed(() => this.touched() && this.invalid());

  readonly touch = output<void>();

  constructor() {
    effect(() => {
      const options = this.options();
      if (options.length == 0) {
        throw new Error(translate('common.radio.no-option'));
      }
      if (options.length < 2) {
        throw new Error(translate('common.radio.min-option'));
      }
    });
  }

  protected radioId(index: number): string {
    const groupName = this.name();
    return `${groupName || 'radio'}-${index}`;
  }

  protected select(value: T): void {
    this.value.update((old) => (this.disabled() ? old : value));
  }

  protected segmentClass(selected: boolean, itemDisabled: boolean): string {
    const base = [
      'relative flex min-h-9 items-center justify-center',
      'rounded-button px-4 text-sm font-semibold',
      'transition-colors duration-300 ease-out',
      'has-[:focus-visible]:ring-2',
      'has-[:focus-visible]:ring-brand-primary',
      'has-[:focus-visible]:ring-offset-1',
    ];
    base.push(selected ? 'bg-brand-primary text-onbrand' : 'text-muted hover:text-brand-primary hover:bg-brand-soft');
    if (itemDisabled || this.disabled()) {
      base.push('cursor-not-allowed pointer-events-none opacity-50');
    } else {
      base.push('cursor-pointer');
    }
    return base.join(' ');
  }
}
