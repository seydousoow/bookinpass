import { Component, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPin } from '@ng-icons/heroicons/outline';
import { IAccommodation } from '@shared/models/stay.model';
import { StarRating } from '../../ui/star-rating';
import { ReviewScore } from '../../ui/review-score';

/** Nom, classement, groupe, adresse et note de l'établissement. */
@Component({
  selector: 'bip-stay-header',
  imports: [NgIcon, StarRating, ReviewScore],
  providers: [provideIcons({ heroMapPin })],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="min-w-0">
        <div class="flex flex-wrap items-center gap-2">
          <h1 class="text-2xl font-bold">{{ accommodation().name }}</h1>
          <bip-star-rating [stars]="accommodation().rating" />
        </div>
        @if (accommodation().brand ?? accommodation().chain; as group) {
          <p class="text-muted text-sm">{{ group.name }}</p>
        }
        <p class="text-muted mt-1 flex items-center gap-1 text-sm">
          <ng-icon aria-hidden="true" name="heroMapPin" />
          {{ address() }}
        </p>
      </div>
      <bip-review-score [count]="accommodation().review_count" [score]="accommodation().review_score" />
    </header>
  `,
})
export class StayHeader {
  readonly accommodation = input.required<IAccommodation>();

  protected address(): string {
    const { line_one, postal_code, city_name } = this.accommodation().location.address;
    return [line_one, [postal_code, city_name].filter(Boolean).join(' ')].filter(Boolean).join(', ');
  }
}
