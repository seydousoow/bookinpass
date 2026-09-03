import {
  booleanAttribute,
  Component,
  computed,
  input,
} from '@angular/core';

@Component({
  selector: 'bip-card',
  host: { '[class]': 'hostClass()' },
  template: `
    @if (title(); as heading) {
      <header class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <h3 class="text-h3 text-muted truncate">{{ heading }}</h3>
          @if (subtitle(); as sub) {
            <p class="text-small text-muted mt-1">{{ sub }}</p>
          }
        </div>
        <ng-content select="[cardActions]" />
      </header>
    }
    <ng-content />
    <ng-content select="[cardFooter]" />
  `,
})
export class Card {
  readonly title = input<string>();
  readonly subtitle = input<string>();
  readonly padding = input<'none' | 'sm' | 'md' | 'lg'>('md');
  readonly interactive = input(false, { transform: booleanAttribute });
  readonly flat = input(false, { transform: booleanAttribute });

  protected readonly hostClass = computed(() => {
    const paddings = { none: '', sm: 'p-3', md: 'p-4', lg: 'p-6' } as const;
    const base = [
      'flex flex-col gap-3 rounded-card border border-border-subtle bg-surface-card',
      paddings[this.padding()],
      this.flat() ? '' : 'shadow-card',
    ];
    if (this.interactive()) {
      base.push(
        'cursor-pointer transition-shadow hover:shadow-panel focus-within:ring-2 focus-within:ring-brand-primary',
      );
    }
    return base.join(' ');
  });
}
