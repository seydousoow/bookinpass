import {
  Component,
  computed,
  input,
  model,
  output,
} from '@angular/core';
import { Dropdown } from '../dropdown/dropdown';
import { Button } from '../button/button';

export interface PassengerCount {
  readonly adults: number;
  readonly children: number;
  readonly infants: number;
}

export type CabinClass = 'economy' | 'premium' | 'business' | 'first';

@Component({
  selector: 'bip-people-selector',
  imports: [Dropdown, Button],
  host: { class: 'block' },
  template: `
    <bip-dropdown [(open)]="open" [label]="label()" [value]="summary()" panelWidth="auto">
      <div class="w-72 p-3">
        @for (row of rows(); track row.key) {
          <div class="flex min-h-11 items-center justify-between gap-3 py-1.5">
            <div class="min-w-0">
              <p class="text-small text-muted font-medium">{{ row.label }}</p>
              <p class="text-label text-muted">{{ row.hint }}</p>
            </div>
            <div class="flex items-center gap-2">
              <button
                bipButton
                variant="outline"
                size="sm"
                type="button"
                [disabled]="row.value <= row.min"
                [attr.aria-label]="'Retirer un ' + row.label"
                (click)="step(row.key, -1)">
                −
              </button>
              <span class="text-small w-6 text-center font-semibold" aria-live="polite">{{ row.value }}</span>
              <button
                bipButton
                variant="outline"
                size="sm"
                type="button"
                [disabled]="row.value >= row.max"
                [attr.aria-label]="'Ajouter un ' + row.label"
                (click)="step(row.key, 1)">
                +
              </button>
            </div>
          </div>
        }

        @if (warning(); as message) {
          <p class="text-label text-state-error border-border-subtle mt-2 border-t pt-2">{{ message }}</p>
        }
      </div>
    </bip-dropdown>
  `,
})
export class PeopleSelector {
  readonly label = input('Voyageurs');
  readonly maxTotal = input(9);
  readonly value = model<PassengerCount>({ adults: 1, children: 0, infants: 0 });
  readonly changed = output<PassengerCount>();

  protected readonly open = model(false);

  protected readonly total = computed(
    () => this.value().adults + this.value().children + this.value().infants,
  );

  protected readonly summary = computed(() => {
    const { adults, children, infants } = this.value();
    const parts = [`${adults} adulte${adults > 1 ? 's' : ''}`];
    if (children) {
      parts.push(`${children} enfant${children > 1 ? 's' : ''}`);
    }
    if (infants) {
      parts.push(`${infants} bébé${infants > 1 ? 's' : ''}`);
    }
    return parts.join(', ');
  });

  /** Un bébé voyage sur les genoux d'un adulte : la contrainte est affichée, pas silencieuse. */
  protected readonly warning = computed(() =>
    this.value().infants > this.value().adults
      ? 'Un bébé doit être accompagné d’un adulte.'
      : null,
  );

  protected readonly rows = computed(() => {
    const { adults, children, infants } = this.value();
    const remaining = this.maxTotal() - this.total();
    return [
      {
        key: 'adults' as const,
        label: 'Adultes',
        hint: '12 ans et plus',
        value: adults,
        min: 1,
        max: adults + Math.max(0, remaining),
      },
      {
        key: 'children' as const,
        label: 'Enfants',
        hint: 'De 2 à 11 ans',
        value: children,
        min: 0,
        max: children + Math.max(0, remaining),
      },
      {
        key: 'infants' as const,
        label: 'Bébés',
        hint: 'Moins de 2 ans',
        value: infants,
        min: 0,
        max: Math.min(adults, infants + Math.max(0, remaining)),
      },
    ];
  });

  protected step(key: keyof PassengerCount, delta: number): void {
    const next = { ...this.value(), [key]: this.value()[key] + delta };
    this.value.set(next);
    this.changed.emit(next);
  }
}
