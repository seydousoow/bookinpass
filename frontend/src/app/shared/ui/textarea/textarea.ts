// noinspection ES6PreferShortImport

import { booleanAttribute, Component, computed, inject, input, model, output } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { Field } from '../field/field';

@Component({
  selector: 'bip-textarea',
  imports: [Field],
  host: { class: 'block' },
  template: `
    <bip-field [errors]="errors()"
               [hint]="counterHint()"
               [label]="label()"
               [name]="name()"
               [required]="required()"
               [showErrors]="touched() && invalid()">
      <textarea (blur)="touch.emit()"
                (input)="onInput($event)"
                [attr.aria-invalid]="touched() && invalid()"
                [attr.maxlength]="maxLength()"
                [class]="controlClass()"
                [disabled]="disabled()"
                [id]="name()"
                [maxLength]="maxLength()"
                [minLength]="minLength()"
                [name]="name()"
                [placeholder]="placeholder()"
                [required]="required()"
                [rows]="rows()"
                [value]="value()"></textarea>
    </bip-field>
  `,
})
export class Textarea implements FormValueControl<string> {
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly hint = input<string>();
  readonly rows = input(4);

  // readonly errors = input<readonly ValidationError.WithOptionalFieldTree[]>([]);

  readonly name = input('');
  readonly touched = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly touch = output<void>();
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly minLength = input<number | undefined>(undefined);
  readonly maxLength = input<number | undefined>(1000);
  readonly hidden = input(false, { transform: booleanAttribute });
  readonly value = model<string>('');

  private readonly transloco = inject(TranslocoService);
  private readonly lang = this.transloco.activeLang;

  /**
   * Indication suivie du compteur. L'indication est traduite ici : la clé collée au compteur
   * (« stay.…-hint — 0/500 ») ne correspondrait plus à aucune traduction dans `bip-field`.
   */
  protected readonly counterHint = computed(() => {
    const max = this.maxLength();
    const key = this.hint();
    if (!max) {
      return key;
    }
    this.lang();
    const hint = key ? this.transloco.translate(key) : '';
    const used = this.value().length;
    return hint ? `${hint} — ${used}/${max}` : `${used}/${max}`;
  });

  protected readonly controlClass = computed(() => {
    const base = ['w-full rounded-field border bg-surface-card text-ink text-sm px-3 py-2.5 transition-colors bip-focusable resize-y'];
    base.push(
      this.touched() && this.invalid() ? 'border-state-error focus-visible:ring-state-error' : 'border-border-strong hover:border-brand-primary/60',
    );
    return base.join(' ');
  });

  protected onInput(event: Event): void {
    const next = (event.target as HTMLTextAreaElement).value;
    this.value.set(next);
  }
}
