import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { DatePipe } from '@core/i18n/date.pipe';
import { IStayReview } from '@shared/models/stay.model';
import { Button } from '@shared/ui/button/button';
import { Spinner } from '@shared/ui/spinner/spinner';
import { ReviewScore } from '../../ui/review-score';
import { StayBookingService } from '../stay-booking.service';

const PAGE_SIZE = 5;

/** Avis clients, cinq par cinq (pagination Duffel par curseur `meta.after`). */
@Component({
  selector: 'bip-stay-reviews',
  imports: [TranslocoDirective, DatePipe, Button, Spinner, ReviewScore],
  template: `
    <section *transloco="let t; prefix: 'stay.reviews'" class="space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="text-xl font-bold">{{ t('title') }}</h2>
        <bip-review-score [count]="count()" [score]="score()" />
      </div>

      @for (review of reviews(); track $index) {
        <article class="rounded-card border-border-subtle border p-3">
          <div class="flex items-center justify-between gap-2">
            <p class="font-semibold">{{ review.reviewer_name === 'Anonymous' ? t('anonymous') : review.reviewer_name }}</p>
            <span class="bg-brand-soft text-brand-dark rounded-button px-1.5 py-0.5 text-sm font-bold">{{ review.score.toFixed(1) }}</span>
          </div>
          <p class="text-muted text-xs">{{ review.created_at | bipDate: { dateStyle: 'long', timeZone: 'UTC' } }}</p>
          <p class="mt-2 text-sm whitespace-pre-line">{{ review.text.trim() }}</p>
        </article>
      } @empty {
        @if (!loading() && !failed()) {
          <p class="text-muted text-sm">{{ t('none') }}</p>
        }
      }

      @if (loading()) {
        <bip-spinner class="block text-center" label="stay.reviews.loading" size="sm" />
      } @else if (failed()) {
        <p class="text-state-error text-sm" role="alert">
          {{ t('failed') }}
          <button (click)="load()" class="font-semibold underline" type="button">{{ t('retry') }}</button>
        </p>
      } @else if (after()) {
        <bip-button (clicked)="load()" variant="outline">{{ t('more') }}</bip-button>
      }
    </section>
  `,
})
export class StayReviews {
  readonly accommodationId = input.required<string>();
  readonly score = input<number | null>(null);
  readonly count = input<number | null>(null);

  protected readonly reviews = signal<readonly IStayReview[]>([]);
  /** Curseur de la page suivante ; `null` : tout est chargé. */
  protected readonly after = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  private readonly api = inject(StayBookingService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      this.accommodationId();
      untracked(() => {
        this.reviews.set([]);
        this.after.set(null);
        this.load();
      });
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .reviews(this.accommodationId(), this.after(), PAGE_SIZE)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ data, meta }) => {
          this.reviews.update((all) => [...all, ...data.reviews]);
          this.after.set(meta.after);
        },
        error: () => this.failed.set(true),
      });
  }
}
