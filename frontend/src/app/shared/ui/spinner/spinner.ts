import { booleanAttribute, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgIcon } from '@ng-icons/core';

@Component({
  selector: 'bip-spinner',
  host: { '[class]': 'hostClass()', role: 'status', 'aria-live': 'polite' },
  template: `
    <ng-icon name="spinner" [size]="sizes[size()]" class="animate-spin" color="var(--color-brand-dark)"/>
    <span [class.sr-only]="!showLabel()" class="text-sm md:text-base font-semibold text-brand-dark">{{ label() | transloco }}</span>
  `,
  imports: [TranslocoPipe, NgIcon],
})
export class Spinner {
  readonly size = input<'xs' | 'sm' | 'md' | 'lg'>('sm');
  readonly showLabel = input(false, { transform: booleanAttribute });
  readonly label = input('loading-in-progress');
  readonly sizes = { xs: '1.5rem', sm: '2.5rem', md: '3.5rem', lg: '4.5rem' } as const;

  protected readonly hostClass = computed(() => {
    return `flex flex-col justify-center items-center gap-2`;
  });
}
