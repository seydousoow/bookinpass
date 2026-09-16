import { booleanAttribute, Component, computed, input, model, output } from '@angular/core';
import { FormCheckboxControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroExclamationCircleSolid } from '@ng-icons/heroicons/solid';

@Component({
  selector: 'bip-checkbox',
  host: { class: 'block', '[class.space-y-1]': '!hideLabel()' },
  templateUrl: './checkbox.html',
  imports: [NgIcon],
  providers: [provideIcons({ heroExclamationCircleSolid })],
})
export class Checkbox implements FormCheckboxControl {
  readonly inputId = input<string>();
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly hint = input<string>();

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);

  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly checked = model(false);

  readonly touch = output<void>();

  readonly controlClass = computed(() => {
    const base = [
      'h-4 w-4 shrink-0 rounded-lg border-2 bip-focusable accent-brand-primary',
      this.touched() && this.invalid() ? 'border-state-error' : 'border-border-strong',
    ];
    if (this.disabled()) {
      base.push('cursor-not-allowed pointer-events-none opacity-50');
    } else {
      base.push('cursor-pointer');
    }
    return base.join(' ');
  });
}
