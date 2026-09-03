import { Component, effect, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { Footer, FooterColumn, Header, NavEntry } from '@shared/ui';

@Component({
  selector: 'bip-root',
  imports: [RouterOutlet, Header, Footer],
  template: `
    <div class="flex min-h-screen flex-col">
      <bip-header [(currentLang)]="currentLang" [cartCount]="0" [entries]="navEntries" />
      <main class="flex-1">
        <router-outlet />
      </main>
      <bip-footer [columns]="footerColumns" />
    </div>
  `,
})
export class App {
  /** Les routes sont décidées ici, pas dans `shared/ui` (plan §2.2 règle 2). */
  protected readonly navEntries: readonly NavEntry[] = [
    { labelKey: 'nav.home', route: '/' },
    { labelKey: 'nav.flights', route: '/flights', disabled: true },
    { labelKey: 'nav.tourism', route: '/tourism', disabled: true },
    { labelKey: 'nav.stays', route: '/stays', disabled: true },
    { labelKey: 'nav.uiKit', route: '/ui-kit' },
  ];
  protected readonly footerColumns: readonly FooterColumn[] = [
    {
      titleKey: 'footer.company',
      links: [
        { labelKey: 'footer.about', route: '/' },
        { labelKey: 'footer.contact', route: '/' },
      ],
    },
    { titleKey: 'footer.help', links: [{ labelKey: 'footer.faq', route: '/' }] },
    {
      titleKey: 'footer.legalTitle',
      links: [
        { labelKey: 'footer.cgv', route: '/' },
        { labelKey: 'footer.privacy', route: '/' },
      ],
    },
  ];

  private readonly transloco = inject(TranslocoService);
  protected currentLang = signal(this.transloco.getActiveLang());

  constructor() {
    effect((): void => {
      this.transloco.setActiveLang(this.currentLang());
    });
  }
}
