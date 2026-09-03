import {
  Component,
  computed,
  forwardRef,
  input,
  model,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'bip-checkbox',
  host: { class: 'block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Checkbox), multi: true },
  ],
  template: `
    <div class="flex min-h-11 items-start gap-2.5 py-2">
      <input
        type="checkbox"
        [id]="inputId()"
        [name]="name() || inputId()"
        [class]="controlClass()"
        [checked]="checked()"
        [disabled]="disabled()"
        [attr.aria-describedby]="hint() ? inputId() + '-hint' : null"
        [attr.aria-invalid]="!!error()"
        (change)="onChange($event)"
        (blur)="onBlur()" />
      <div class="min-w-0 flex-1">
        <label
          [for]="inputId()"
          class="text-small text-muted block cursor-pointer font-medium select-none">
          {{ label() }}
        </label>
        @if (error()) {
          <p class="text-label text-state-error mt-0.5">{{ error() }}</p>
        } @else if (hint()) {
          <p [id]="inputId() + '-hint'" class="text-label text-muted mt-0.5">{{ hint() }}</p>
        }
      </div>
    </div>
  `,
})
export class Checkbox implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly name = input<string>();
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly disabled = model(false);
  readonly checked = model(false);

  protected readonly controlClass = computed(() => {
    const base = [
      'mt-0.5 h-5 w-5 shrink-0 cursor-pointer rounded border-2 bip-focusable',
      'accent-brand-primary',
      this.error() ? 'border-state-error' : 'border-border-strong',
    ];
    if (this.disabled()) {
      base.push('cursor-not-allowed opacity-50');
    }
    return base.join(' ');
  });

  private onChangeFn: (value: boolean) => void = () => undefined;
  private onTouchedFn: () => void = () => undefined;

  protected onChange(event: Event): void {
    const next = (event.target as HTMLInputElement).checked;
    this.checked.set(next);
    this.onChangeFn(next);
  }

  protected onBlur(): void {
    this.onTouchedFn();
  }

  writeValue(value: boolean): void {
    this.checked.set(!!value);
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
