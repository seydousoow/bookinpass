import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'bip-spinner',
  host: { '[class]': 'hostClass()', role: 'status', 'aria-live': 'polite' },
  template: `
    <svg class="h-full w-full animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-opacity="0.25" stroke-width="3" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
    </svg>
    <span class="sr-only">{{ label() }}</span>
  `,
})
export class Spinner {
  readonly size = input<'xs' | 'sm' | 'md' | 'lg'>('sm');
  readonly label = input('Chargement en cours');

  protected readonly hostClass = computed(() => {
    const sizes = { xs: 'h-3 w-3', sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-8 w-8' } as const;
    return `inline-block shrink-0 ${sizes[this.size()]}`;
  });
}
