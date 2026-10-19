import { Component, computed, input, model } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroChevronLeft, heroChevronRight } from '@ng-icons/heroicons/outline';

/** `…` entre deux numéros non contigus. */
type TPageItem = number | 'gap';

/**
 * Pages d'une liste : précédente / suivante, première et dernière page toujours visibles, la page courante et ses
 * voisines, `…` entre deux. Masquée s'il n'y a qu'une page.
 */
export function pageItems(current: number, count: number, siblings = 1): TPageItem[] {
  const pages = new Set([1, count]);
  for (let p = current - siblings; p <= current + siblings; p++) {
    if (p >= 1 && p <= count) {
      pages.add(p);
    }
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const items: TPageItem[] = [];
  sorted.forEach((page, i) => {
    const previous = sorted[i - 1];
    if (previous !== undefined && page - previous === 2) {
      items.push(page - 1); // un seul numéro manquant : on l'affiche plutôt qu'un « … »
    } else if (previous !== undefined && page - previous > 2) {
      items.push('gap');
    }
    items.push(page);
  });
  return items;
}

@Component({
  selector: 'bip-pagination',
  imports: [TranslocoDirective, NgIcon],
  providers: [provideIcons({ heroChevronLeft, heroChevronRight })],
  host: { class: 'block' },
  template: `
    @if (pageCount() > 1) {
      <nav *transloco="let t" [attr.aria-label]="t('common.pagination.label')" class="flex flex-wrap items-center justify-between gap-2">
        <p aria-live="polite" class="text-muted text-xs">{{ t('common.pagination.summary', {from: from(), to: to(), total: total()}) }}</p>
        <ul class="flex items-center gap-1">
          <li>
            <button (click)="go(page() - 1)" [disabled]="page() === 1" [class]="arrowClass" type="button">
              <span class="sr-only">{{ t('common.pagination.previous') }}</span>
              <ng-icon aria-hidden="true" name="heroChevronLeft"/>
            </button>
          </li>
          @for (item of items(); track $index) {
            <li>
              @if (item === 'gap') {
                <span aria-hidden="true" class="text-muted px-1 text-sm">…</span>
              } @else {
                <button (click)="go(item)"
                        [attr.aria-current]="item === page() ? 'page' : null"
                        [attr.aria-label]="t('common.pagination.page', {page: item})"
                        [class]="pageClass(item === page())"
                        type="button">
                  {{ item }}
                </button>
              }
            </li>
          }
          <li>
            <button (click)="go(page() + 1)" [disabled]="page() === pageCount()" [class]="arrowClass" type="button">
              <span class="sr-only">{{ t('common.pagination.next') }}</span>
              <ng-icon aria-hidden="true" name="heroChevronRight"/>
            </button>
          </li>
        </ul>
      </nav>
    }
  `,
})
export class Pagination {
  /** Page courante, à partir de 1. */
  readonly page = model(1);
  /** Nombre total d'éléments de la liste. */
  readonly total = input.required<number>();
  readonly pageSize = input(15);

  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  protected readonly items = computed(() => pageItems(this.page(), this.pageCount()));
  protected readonly from = computed(() => (this.page() - 1) * this.pageSize() + 1);
  protected readonly to = computed(() => Math.min(this.total(), this.page() * this.pageSize()));

  protected readonly arrowClass =
    'bip-focusable rounded-button flex size-9 items-center justify-center text-ink hover:bg-brand-soft disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent';

  protected pageClass(current: boolean): string {
    return [
      'bip-focusable rounded-button flex size-9 items-center justify-center text-sm font-semibold',
      current ? 'bg-brand-primary text-onbrand' : 'text-ink hover:bg-brand-soft',
    ].join(' ');
  }

  protected go(page: number): void {
    if (page >= 1 && page <= this.pageCount() && page !== this.page()) {
      this.page.set(page);
    }
  }
}
