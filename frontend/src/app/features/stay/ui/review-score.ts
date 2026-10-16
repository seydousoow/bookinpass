import { Component, computed, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';

/** Note des avis clients (sur 10), son appréciation et le nombre d'avis. */
@Component({
  selector: 'bip-review-score',
  imports: [TranslocoDirective],
  host: { class: 'inline-flex' },
  template: `
    @if (score() !== null) {
      <span *transloco="let t; prefix: 'stay.reviews'" class="inline-flex items-center gap-2 text-sm">
        <span class="bg-brand-primary text-onbrand rounded-button px-1.5 py-0.5 font-bold">{{ score()!.toFixed(1) }}</span>
        <span class="font-semibold">{{ t('levels.' + level()) }}</span>
        @if (count(); as total) {
          <span class="text-muted text-xs">{{ t('count', { count: total }) }}</span>
        }
      </span>
    }
  `,
})
export class ReviewScore {
  readonly score = input<number | null>(null);
  readonly count = input<number | null>(null);
  protected readonly level = computed(() => {
    const score = this.score() ?? 0;
    return score >= 9 ? 'exceptional' : score >= 8 ? 'very-good' : score >= 7 ? 'good' : 'fair';
  });
}
