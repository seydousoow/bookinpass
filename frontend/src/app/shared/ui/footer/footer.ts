import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgOptimizedImage } from '@angular/common';

/**
 * Les colonnes sont fournies par l'appelant : `shared/ui` ne connaît ni le
 * métier ni les routes (plan §2.2 règle 2). Ce composant ne reçoit que des
 * clés de traduction et des chemins, il ne les décide pas.
 */
export interface FooterColumn {
  readonly titleKey: string;
  readonly links: readonly { readonly labelKey: string; readonly route: string }[];
}

@Component({
  selector: 'bip-footer',
  imports: [RouterLink, TranslocoDirective, NgOptimizedImage],
  host: { class: 'block z-20' },
  template: `
    <footer *transloco="let t" class="border-border-subtle bg-surface-card border-t">
      <div class="mx-auto grid max-w-7xl gap-8 px-4 py-10 sm:grid-cols-2 lg:grid-cols-4">
        <div class="w-64">
          <div class="relative h-18 w-full">
            <img alt="Book In Pass" fill ngSrc="/images/bip-transparent-logo.png" priority />
          </div>
          <p class="text-muted mt-3 text-sm">{{ t('footer.tagline') }}</p>
        </div>

        @for (column of columns(); track column.titleKey) {
          <nav [attr.aria-label]="t(column.titleKey)">
            <h2 class="text-ink text-sm font-semibold">{{ t(column.titleKey) }}</h2>
            <ul class="mt-3 space-y-1">
              @for (link of column.links; track link.labelKey) {
                <li>
                  <a
                    [routerLink]="link.route"
                    class="text-muted hover:text-brand-primary bip-focusable rounded-button inline-flex min-h-11 items-center text-sm">
                    {{ t(link.labelKey) }}
                  </a>
                </li>
              }
            </ul>
          </nav>
        }
      </div>

      <div class="border-border-subtle border-t">
        <p class="text-muted mx-auto max-w-7xl px-4 py-4 text-xs">© {{ year }} Book In Pass · {{ t('footer.legal') }}</p>
      </div>
    </footer>
  `,
})
export class Footer {
  readonly columns = input<readonly FooterColumn[]>([]);
  protected readonly year = new Date().getFullYear();
}
