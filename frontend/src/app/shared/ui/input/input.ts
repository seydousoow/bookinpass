import { booleanAttribute, Component, computed, inject, input, model, output } from '@angular/core';
// noinspection ES6PreferShortImport
import { Field } from '../field/field';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { TranslocoService } from '@jsverse/transloco';

export type InputSize = 'sm' | 'md' | 'lg';

const sizesStyling = {
  sm: 'min-h-9 px-3 py-1.5 text-sm',
  md: 'min-h-11 px-3 py-2.5 text-sm',
  lg: 'min-h-12 px-4 py-3 text-base',
} satisfies Record<InputSize, string>;

@Component({
  selector: 'bip-input',
  imports: [Field],
  host: { '[class.hidden]': 'hidden()', '[class.block]': '!hidden()' },
  template: `
    <bip-field [errors]="errors()" [hideLabel]="hideLabel()" [hint]="hint()" [label]="label()" [name]="name()" [required]="required()" [showErrors]="touched() && invalid()">
      <div class="relative flex items-center">
        @if (prefix()) {
          <span class="text-muted pointer-events-none absolute left-3 flex items-center text-sm">{{ prefix() }}</span>
        }
        <input (blur)="touch.emit()"
               (input)="onInput($event)"
               [attr.aria-describedby]="describedBy()"
               [attr.aria-invalid]="touched() && invalid()"
               [attr.autocomplete]="autocomplete()"
               [attr.max]="dateMax()"
               [attr.maxlength]="maxLength()"
               [class]="controlClass()"
               [disabled]="disabled()"
               [id]="name()"
               [name]="name()"
               [placeholder]="_placeholder()"
               [readOnly]="readonly()"
               [required]="required()"
               [type]="type()"
               [value]="value() ?? ''" />
        @if (suffix()) {
          <span class="text-muted pointer-events-none absolute right-3 flex items-center text-sm">{{ suffix() }}</span>
        }
      </div>
    </bip-field>
  `,
})
export class TextInput<T = string> implements FormValueControl<T | null> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });

  readonly type = input<'text' | 'email' | 'tel' | 'number' | 'password' | 'search' | 'date'>('text');
  /** Borne haute native — utile pour `type="date"` (ex. : pas de date de naissance future). */
  readonly dateMax = input<string>();
  readonly placeholder = input<string>();
  readonly hint = input<string>();
  readonly prefix = input<string>();
  readonly suffix = input<string>();
  readonly size = input<InputSize>('md');
  readonly autocomplete = input<string>();

  readonly name = input('');
  readonly touched = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly dirty = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly touch = output<void>();
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly maxLength = input<number | undefined>(undefined);
  readonly minLength = input<number | undefined>(undefined);
  readonly hidden = input(false, { transform: booleanAttribute });
  readonly value = model<T | null>(null);

  protected readonly describedBy = computed(() => {
    if (this.touched() && this.invalid()) return `${this.name()}-error`;
    if (this.hint()) return `${this.name()}-hint`;
    return null;
  });

  protected readonly controlClass = computed(() => {
    const base = [
      'w-full rounded-field border bg-surface-card text-ink placeholder:text-muted/70 transition-colors bip-focusable',
      sizesStyling[this.size()],
    ];
    if (this.prefix()) {
      base.push('pl-9');
    }
    if (this.suffix()) {
      base.push('pr-9');
    }
    if (this.touched() && this.invalid()) {
      base.push('border-state-error focus-visible:ring-state-error');
    } else {
      base.push('border-border-strong hover:border-brand-primary/60');
    }
    if (this.disabled()) {
      base.push('bg-surface-muted cursor-not-allowed opacity-60');
    }
    if (this.readonly()) {
      base.push('bg-surface-muted');
    }
    return base.join(' ');
  });

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value as T);
  }

  private readonly transloco = inject(TranslocoService);
  readonly _placeholder = computed(() => {
    const value = this.placeholder();
    return value && value.length > 0 ? this.transloco.translate(value) : '';
  });
}
