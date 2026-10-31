import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyStore } from '@core/money/currency.store';
import { RouterOutlet } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
// Par leur chemin, pas par le barrel `@shared/ui` : il entraînerait tout le kit (date-picker, recherche d'aéroports,
// CDK…) dans le chargement initial au lieu des pages qui s'en servent.
import { Footer, FooterColumn } from '@shared/ui/footer/footer';
import { Header, NavEntry } from '@shared/ui/header/header';
import { firstValueFrom } from 'rxjs';
import { ICurrencyOption } from '@shared/models/money.model';
import { injectSpeedInsights } from '@vercel/speed-insights';

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
export class App implements OnInit {
  protected readonly navEntries: readonly NavEntry[] = [
    { labelKey: 'nav.home', route: '/', disabled: false },
    { labelKey: 'nav.flights', route: '/flight', disabled: false },
    { labelKey: 'nav.tourism', route: '/tourism', disabled: true },
    { labelKey: 'nav.stays', route: '/stays', disabled: false },
    { labelKey: 'nav.bus', route: '/bus', disabled: false },
    { labelKey: 'nav.uiKit', route: '/ui-kit', disabled: false },
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

  ngOnInit() {
    injectSpeedInsights();
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
