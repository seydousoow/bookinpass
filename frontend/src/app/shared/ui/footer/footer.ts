import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';

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
  imports: [RouterLink, TranslocoDirective],
  host: { class: 'block z-20' },
  template: `
    <footer *transloco="let t" class="border-border-subtle bg-surface-card border-t">
      <div class="mx-auto grid max-w-7xl gap-8 px-4 py-10 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <div class="flex items-center gap-2">
            <span class="bg-brand-primary text-onbrand rounded-button grid h-9 w-9 place-items-center font-bold">B</span>
            <span class="text-xl text-brand-dark font-bold">Book In Pass</span>
          </div>
          <p class="text-sm text-muted mt-3">{{ t('footer.tagline') }}</p>
        </div>

        @for (column of columns(); track column.titleKey) {
          <nav [attr.aria-label]="t(column.titleKey)">
            <h2 class="text-sm text-ink font-semibold">{{ t(column.titleKey) }}</h2>
            <ul class="mt-3 space-y-1">
              @for (link of column.links; track link.labelKey) {
                <li>
                  <a
                    [routerLink]="link.route"
                    class="text-sm text-muted hover:text-brand-primary bip-focusable rounded-button inline-flex min-h-11 items-center">
                    {{ t(link.labelKey) }}
                  </a>
                </li>
              }
            </ul>
          </nav>
        }
      </div>

      <div class="border-border-subtle border-t">
        <p class="text-xs text-muted mx-auto max-w-7xl px-4 py-4">© {{ year }} Book In Pass · {{ t('footer.legal') }}</p>
      </div>
    </footer>
  `,
})
export class Footer {
  readonly columns = input<readonly FooterColumn[]>([]);
  protected readonly year = new Date().getFullYear();
}
