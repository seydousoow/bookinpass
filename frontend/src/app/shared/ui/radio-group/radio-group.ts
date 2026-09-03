import {
  Component,
  forwardRef,
  input,
  model,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { SelectOption } from '@shared/models/select.model';

/**
 * Groupe de boutons radio. Deux présentations : liste verticale, ou segments
 * horizontaux (utilisés par le sélecteur aller simple / aller-retour).
 */
@Component({
  selector: 'bip-radio-group',
  host: { class: 'block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => RadioGroup), multi: true },
  ],
  template: `
    <fieldset [attr.aria-invalid]="!!error()">
      @if (label()) {
        <legend class="text-small text-muted mb-2 flex items-center gap-1 font-semibold">
          {{ label() }}
          @if (required()) {
            <span class="text-state-error" aria-hidden="true">*</span>
          }
        </legend>
      }

      @if (appearance() === 'segmented') {
        <div class="border-border-subtle bg-surface-muted rounded-field inline-flex gap-1 border p-1" role="radiogroup">
          @for (option of options(); track option.value) {
            <label
              [class]="segmentClass(option.value === value(), !!option.disabled)"
              class="min-h-9">
              <input
                type="radio"
                class="sr-only"
                [name]="groupName()"
                [value]="option.value"
                [checked]="option.value === value()"
                [disabled]="disabled() || !!option.disabled"
                (change)="select(option.value)"
                (blur)="onBlur()" />
              {{ option.label }}
            </label>
          }
        </div>
      } @else {
        <div class="flex flex-col gap-1" role="radiogroup">
          @for (option of options(); track option.value; let i = $index) {
            <div class="flex min-h-11 items-start gap-2.5 py-1.5">
              <input
                type="radio"
                [id]="groupName() + '-' + i"
                [name]="groupName()"
                [value]="option.value"
                [checked]="option.value === value()"
                [disabled]="disabled() || !!option.disabled"
                class="border-border-strong accent-brand-primary bip-focusable mt-0.5 h-5 w-5 shrink-0 cursor-pointer border-2"
                (change)="select(option.value)"
                (blur)="onBlur()" />
              <label
                [for]="groupName() + '-' + i"
                class="text-small text-muted block cursor-pointer font-medium">
                {{ option.label }}
                @if (option.hint) {
                  <span class="text-label text-muted block font-normal">{{ option.hint }}</span>
                }
              </label>
            </div>
          }
        </div>
      }

      @if (error()) {
        <p class="text-label text-state-error mt-1">{{ error() }}</p>
      }
    </fieldset>
  `,
})
export class RadioGroup<T = string> implements ControlValueAccessor {
  readonly groupName = input.required<string>();
  readonly options = input.required<readonly SelectOption<T>[]>();
  readonly label = input<string>();
  readonly error = input<string>();
  readonly required = input(false);
  readonly appearance = input<'list' | 'segmented'>('list');
  readonly disabled = model(false);
  readonly value = model<T | null>(null);

  protected segmentClass(selected: boolean, itemDisabled: boolean): string {
    const base = [
      'flex cursor-pointer items-center justify-center rounded-button px-4 text-small font-semibold transition-colors',
      'has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-brand-primary has-[:focus-visible]:ring-offset-1',
    ];
    base.push(
      selected
        ? 'bg-brand-primary text-onbrand'
        : 'text-muted hover:text-brand-primary hover:bg-surface-card',
    );
    if (itemDisabled || this.disabled()) {
      base.push('pointer-events-none opacity-50');
    }
    return base.join(' ');
  }

  private onChangeFn: (value: T | null) => void = () => undefined;
  private onTouchedFn: () => void = () => undefined;

  protected select(next: T): void {
    this.value.set(next);
    this.onChangeFn(next);
  }

  protected onBlur(): void {
    this.onTouchedFn();
  }

  writeValue(value: T | null): void {
    this.value.set(value);
  }

  registerOnChange(fn: (value: T | null) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
