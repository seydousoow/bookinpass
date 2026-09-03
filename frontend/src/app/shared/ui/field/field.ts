import { Component, input } from '@angular/core';

/**
 * Habillage commun à tous les champs : libellé, obligatoire, aide, erreur.
 * Un message d'erreur n'est jamais porté par la couleur seule (cahier §11) —
 * il est toujours accompagné d'un texte et rattaché au champ par aria-describedby.
 */
@Component({
  selector: 'bip-field',
  host: { class: 'flex flex-col gap-1.5' },
  template: `
    @if (label() && !hideLabel()) {
      <label [attr.for]="for()" class="text-small text-ink flex items-center gap-1 font-semibold">
        {{ label() }}
        @if (required()) {
          <span class="text-state-error" aria-hidden="true">*</span>
        }
      </label>
    } @else if (label()) {
      <label [attr.for]="for()" class="sr-only">{{ label() }}</label>
    }

    <ng-content />

    @if (error()) {
      <p [id]="for() + '-error'" class="text-label text-state-error flex items-start gap-1">
        <svg class="mt-px h-3.5 w-3.5 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fill-rule="evenodd"
            d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm0-13a.75.75 0 0 1 .75.75v4.5a.75.75 0 0 1-1.5 0v-4.5A.75.75 0 0 1 10 5Zm0 9.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
            clip-rule="evenodd" />
        </svg>
        <span>{{ error() }}</span>
      </p>
    } @else if (hint()) {
      <p [id]="for() + '-hint'" class="text-label text-muted">{{ hint() }}</p>
    }
  `,
})
export class Field {
  readonly label = input<string>();
  readonly for = input<string>('');
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly required = input(false);
  readonly hideLabel = input(false);
}
