import { Component, inject, signal } from '@angular/core';
import { CurrencyStore } from '@core/money/currency.store';
import { RouterOutlet } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { Footer, FooterColumn, Header, NavEntry } from '@shared/ui';
import { firstValueFrom } from 'rxjs';
import { ICurrencyOption } from '@shared/models/money.model';

@Component({
  selector: 'bip-root',
  imports: [RouterOutlet, Header, Footer],
  template: `
    <div class="flex min-h-screen flex-col">
      <bip-header
        (currentCurrencyChange)="currencyChange($event)"
        (currentLangChange)="changeLanguage($event)"
        [cartCount]="cartCount()"
        [currencies]="_currencies"
        [currentCurrency]="_currentCurrency"
        [currentLang]="_currentLang"
        [entries]="navEntries"
        [languages]="languages" />
      <main class="flex-1">
        <router-outlet />
      </main>
      <bip-footer [columns]="footerColumns" />
    </div>
  `,
})
export class App {
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
  protected cartCount = signal(0);

  /** La devise d'affichage est indépendante de la langue (cadrage §11). */
  private readonly currencyStore = inject(CurrencyStore);
  private readonly transloco = inject(TranslocoService);

  readonly languages = this.transloco.getAvailableLangs();
  private readonly currentLang = signal(this.transloco.getActiveLang());

  get _currentLang(): string {
    return this.currentLang();
  }

  get _currentCurrency(): string {
    return this.currencyStore.selected();
  }

  get _currencies(): readonly ICurrencyOption[] {
    return this.currencyStore.currencies();
  }

  protected async changeLanguage(lang: string): Promise<void> {
    if (lang === this.currentLang()) {
      return;
    }

    await firstValueFrom(this.transloco.load(lang));
    this.transloco.setActiveLang(lang);
    this.currentLang.set(lang);
  }

  protected currencyChange(currency: string): void {
    this.currencyStore.select(currency);
  }
}
