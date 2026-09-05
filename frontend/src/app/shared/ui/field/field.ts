import { booleanAttribute, Component, input } from '@angular/core';
import { ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroExclamationCircleSolid } from '@ng-icons/heroicons/solid';

@Component({
  selector: 'bip-field',
  host: { class: 'flex flex-col gap-1.5' },
  providers: [provideIcons({ heroExclamationCircleSolid })],
  template: `
    @if (label() && !hideLabel()) {
      <label [attr.for]="inputId()" class="text-small text-ink flex items-center gap-1 font-semibold">
        {{ label() }}
        @if (required()) {
          <span aria-hidden="true" class="text-state-error">*</span>
        }
      </label>
    } @else if (label()) {
      <label [attr.for]="inputId()" class="sr-only">{{ label() }}</label>
    }

    <ng-content />

    @if (showErrors()) {
      @for (error of errors(); track error) {
        <p class="text-label text-state-error flex items-center gap-1">
          <ng-icon color="red" name="heroExclamationCircleSolid" />
          <span>{{ error.message }}</span>
        </p>
      }
    } @else if (hint()) {
      <p [id]="inputId() + '-hint'" class="text-label text-muted">{{ hint() }}</p>
    }
  `,
  imports: [NgIcon],
})
export class Field {
  readonly label = input.required<string>();
  readonly inputId = input.required<string>();
  readonly hint = input<string>();
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly showErrors = input(false);

  readonly required = input(false, { transform: booleanAttribute });
  readonly hideLabel = input(false, { transform: booleanAttribute });
}
