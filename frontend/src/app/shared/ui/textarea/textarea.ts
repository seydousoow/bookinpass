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

@Component({
  selector: 'bip-textarea',
  imports: [Field],
  host: { class: 'block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Textarea), multi: true },
  ],
  template: `
    <bip-field
      [label]="label()"
      [for]="inputId()"
      [hint]="counterHint()"
      [error]="error()"
      [required]="required()">
      <textarea
        [id]="inputId()"
        [name]="name() || inputId()"
        [class]="controlClass()"
        [rows]="rows()"
        [placeholder]="placeholder()"
        [disabled]="disabled()"
        [required]="required()"
        [attr.maxlength]="maxLength()"
        [attr.aria-invalid]="!!error()"
        [value]="value() ?? ''"
        (input)="onInput($event)"
        (blur)="onBlur()"></textarea>
    </bip-field>
  `,
})
export class Textarea implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input<string>();
  readonly name = input<string>();
  readonly placeholder = input('');
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly rows = input(4);
  readonly maxLength = input<number>();
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = model(false);
  readonly value = model<string | null>('');

  protected readonly counterHint = computed(() => {
    const max = this.maxLength();
    if (!max) {
      return this.hint();
    }
    const used = (this.value() ?? '').length;
    return this.hint() ? `${this.hint()} — ${used}/${max}` : `${used}/${max}`;
  });

  protected readonly controlClass = computed(() => {
    const base = [
      'w-full rounded-field border bg-surface-card text-ink text-small',
      'px-3 py-2.5 transition-colors bip-focusable resize-y',
      this.error()
        ? 'border-state-error focus-visible:ring-state-error'
        : 'border-border-strong hover:border-brand-primary/60',
    ];
    if (this.disabled()) {
      base.push('bg-surface-muted cursor-not-allowed opacity-60');
    }
    return base.join(' ');
  });

  private onChangeFn: (value: string) => void = () => undefined;
  private onTouchedFn: () => void = () => undefined;

  protected onInput(event: Event): void {
    const next = (event.target as HTMLTextAreaElement).value;
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
