import {
  booleanAttribute,
  Component,
  computed,
  forwardRef,
  input,
  model,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Field } from '../field/field';

export type InputSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'bip-input',
  imports: [Field],
  host: { class: 'block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextInput), multi: true },
  ],
  template: `
    <bip-field
      [label]="label()"
      [for]="inputId()"
      [hint]="hint()"
      [error]="error()"
      [required]="required()"
      [hideLabel]="hideLabel()">
      <div class="relative flex items-center">
        @if (prefix()) {
          <span
            class="text-muted text-small pointer-events-none absolute left-3 flex items-center">
            {{ prefix() }}
          </span>
        }
        <input
          [id]="inputId()"
          [type]="type()"
          [name]="name() || inputId()"
          [class]="controlClass()"
          [value]="value() ?? ''"
          [placeholder]="placeholder()"
          [disabled]="disabled()"
          [readOnly]="readonly()"
          [required]="required()"
          [attr.inputmode]="inputMode()"
          [attr.autocomplete]="autocomplete()"
          [attr.maxlength]="maxLength()"
          [attr.aria-invalid]="!!error()"
          [attr.aria-describedby]="describedBy()"
          (input)="onInput($event)"
          (blur)="onBlur()" />
        @if (suffix()) {
          <span
            class="text-muted text-small pointer-events-none absolute right-3 flex items-center">
            {{ suffix() }}
          </span>
        }
      </div>
    </bip-field>
  `,
})
export class TextInput implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input<string>();
  readonly name = input<string>();
  readonly type = input<'text' | 'email' | 'tel' | 'number' | 'password' | 'search'>('text');
  readonly placeholder = input('');
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly prefix = input<string>();
  readonly suffix = input<string>();
  readonly size = input<InputSize>('md');
  readonly required = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly inputMode = input<string>();
  readonly autocomplete = input<string>();
  readonly maxLength = input<number>();

  /** Writable : `setDisabledState` du CVA doit pouvoir l'écrire. */
  readonly disabled = model(false);
  readonly value = model<string | null>('');

  protected readonly describedBy = computed(() =>
    this.error() ? `${this.inputId()}-error` : this.hint() ? `${this.inputId()}-hint` : null,
  );

  protected readonly controlClass = computed(() => {
    const sizes = {
      sm: 'min-h-9 px-3 py-1.5 text-small',
      md: 'min-h-11 px-3 py-2.5 text-small',
      lg: 'min-h-12 px-4 py-3 text-body',
    } as const;
    const base = [
      'w-full rounded-field border bg-surface-card text-ink',
      'placeholder:text-muted/70 transition-colors bip-focusable',
      sizes[this.size()],
    ];
    if (this.prefix()) {
      base.push('pl-9');
    }
    if (this.suffix()) {
      base.push('pr-9');
    }
    base.push(
      this.error()
        ? 'border-state-error focus-visible:ring-state-error'
        : 'border-border-strong hover:border-brand-primary/60',
    );
    if (this.disabled()) {
      base.push('bg-surface-muted cursor-not-allowed opacity-60');
    }
    if (this.readonly()) {
      base.push('bg-surface-muted');
    }
    return base.join(' ');
  });

  private onChangeFn: (value: string) => void = () => undefined;
  private onTouchedFn: () => void = () => undefined;

  protected onInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChangeFn(next);
  }

  protected onBlur(): void {
    this.onTouchedFn();
  }

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
