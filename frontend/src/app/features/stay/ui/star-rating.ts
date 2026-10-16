import { Component, computed, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroStarSolid } from '@ng-icons/heroicons/solid';

/** Classement en étoiles (1 à 5) ; rien pour un établissement non classé. */
@Component({
  selector: 'bip-star-rating',
  imports: [NgIcon, TranslocoDirective],
  providers: [provideIcons({ heroStarSolid })],
  host: { class: 'inline-flex' },
  template: `
    @if (stars(); as count) {
      <span *transloco="let t" [attr.aria-label]="t('stay.rating.stars', { count })" class="text-state-warning inline-flex" role="img">
        @for (star of starList(); track $index) {
          <ng-icon aria-hidden="true" name="heroStarSolid" size="0.9rem" />
        }
      </span>
    }
  `,
})
export class StarRating {
  readonly stars = input<number | null>(null);
  protected readonly starList = computed(() => Array.from({ length: Math.min(5, Math.max(0, this.stars() ?? 0)) }));
}
