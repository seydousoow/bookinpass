import { Component, computed, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { IAccommodation } from '@shared/models/stay.model';

/** Équipements de l'établissement, en pastilles. `limit` : les premiers seulement, avec « +n ». */
@Component({
  selector: 'bip-amenity-list',
  imports: [TranslocoDirective],
  template: `
    <ul *transloco="let t; prefix: 'stay.amenities'" class="flex flex-wrap gap-1.5">
      @for (amenity of shown(); track amenity.type) {
        <li class="border-border-subtle rounded-pill border px-2 py-0.5 text-xs">{{ t(amenity.type) }}</li>
      }
      @if (hidden(); as more) {
        <li class="text-muted px-1 py-0.5 text-xs">{{ t('more', { count: more }) }}</li>
      }
    </ul>
  `,
})
export class AmenityList {
  readonly amenities = input.required<IAccommodation['amenities']>();
  readonly limit = input<number | null>(null);
  protected readonly shown = computed(() => {
    const all = this.amenities() ?? [];
    const limit = this.limit();
    return limit === null ? all : all.slice(0, limit);
  });
  protected readonly hidden = computed(() => (this.amenities()?.length ?? 0) - this.shown().length);
}
