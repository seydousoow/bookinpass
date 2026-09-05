import { Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'brand' | 'accent' | 'success' | 'warning' | 'error' | 'info';

@Component({
  selector: 'bip-badge',
  host: { '[class]': 'hostClass()' },
  template: `<ng-content />`,
})
export class Badge {
  readonly tone = input<BadgeTone>('neutral');
  readonly size = input<'sm' | 'md'>('md');

  protected readonly hostClass = computed(() => {
    const tones = {
      neutral: 'bg-surface-muted text-muted border-border-subtle',
      brand: 'bg-brand-soft text-brand-dark border-brand-soft',
      accent: 'bg-brand-accent/12 text-brand-accent border-brand-accent/30',
      success: 'bg-state-success-soft text-state-success border-state-success/30',
      warning: 'bg-state-warning-soft text-ink border-state-warning/40',
      error: 'bg-state-error-soft text-state-error border-state-error/30',
      info: 'bg-state-info-soft text-state-info border-state-info/30',
    } as const;
    const sizes = { sm: 'px-2 py-0.5 text-label', md: 'px-2.5 py-1 text-label', } as const;
    return `inline-flex items-center gap-1 rounded-pill border font-semibold ${tones[this.tone()]} ${sizes[this.size()]}`;
  });
}
