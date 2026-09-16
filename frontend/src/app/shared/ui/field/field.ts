import { booleanAttribute, Component, input } from '@angular/core';
import { ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroExclamationCircleSolid } from '@ng-icons/heroicons/solid';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'bip-field',
  host: { class: 'flex flex-col' },
  providers: [provideIcons({ heroExclamationCircleSolid })],
  template: `
    @if (label() && !hideLabel()) {
      <label [attr.for]="name()" class="text-xs text-muted mb-1 block font-semibold">
        {{ label() | transloco }}
        @if (required()) {
          <span aria-hidden="true" class="text-state-error">*</span>
        }
      </label>
    } @else if (label()) {
      <label [attr.for]="name()" class="sr-only">{{ label() | transloco }}</label>
    }

    <ng-content />

    @if (showErrors()) {
      @for (error of errors(); track error) {
        <p class="text-xs text-state-error flex items-center gap-1">
          <ng-icon color="red" name="heroExclamationCircleSolid" />
          <span>{{ error.message | transloco }}</span>
        </p>
      }
    } @else if (hint()) {
      <p [id]="name() + '-hint'" class="text-xs text-muted">{{ hint() | transloco }}</p>
    }
  `,
  imports: [NgIcon, TranslocoPipe],
})
export class Field {
  readonly label = input.required<string>();
  readonly name = input.required<string>();
  readonly hint = input<string>();
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly showErrors = input(false);

  readonly required = input(false, { transform: booleanAttribute });
  readonly hideLabel = input(false, { transform: booleanAttribute });
}
