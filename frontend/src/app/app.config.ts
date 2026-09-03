import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideTransloco, TranslocoConfig } from '@jsverse/transloco';
import { provideTranslocoLocale, TranslocoLocaleConfig } from '@jsverse/transloco-locale';
import { cookiesStorage, GetLangParams, provideTranslocoPersistLang } from '@jsverse/transloco-persist-lang';
import { routes } from './app.routes';
import { APP_CONFIG } from '@core/config/app-config.token';
import { HttpTranslocoLoader } from '@core/i18n/transloco-loader';
import { environment } from '@env';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr-SN';
import localeEN from '@angular/common/locales/en';

registerLocaleData(localeFr);
registerLocaleData(localeEN);

const availableAppLangs: readonly string[] = ['fr', 'en'];
const langToLocaleMapping: Record<string, string> = { fr: 'fr-SN', en: 'en-US' };

const translocoConfig = (): Partial<TranslocoConfig> => ({
  availableLangs: [...availableAppLangs],
  defaultLang: 'fr',
  fallbackLang: 'fr',
  reRenderOnLangChange: true,
  prodMode: environment.production,
  flatten: { aot: environment.production },
});

const localeConfig: TranslocoLocaleConfig = {
  defaultCurrency: 'XOF',
  defaultLocale: 'fr-SN',
  // `localeToCurrencyMapping` l'emporte sur `defaultCurrency` (le service lit
  // `mapping[locale] || defaultCurrency`). Or la table livrée par la librairie
  // associe 'fr-SN' à EUR — d'où des prix en euros sans cette correction.
  // Book In Pass vend en francs CFA quelle que soit la langue d'affichage.
  localeToCurrencyMapping: { 'fr-SN': 'XOF', 'en-US': 'XOF' },
  localeConfig: {
    global: { date: { dateStyle: 'long', timeStyle: 'long' }, currency: { minimumFractionDigits: 0, maximumFractionDigits: 2 } },
    localeBased: { 'fr-SN': { currency: { minimumFractionDigits: 0, maximumFractionDigits: 0 } } },
  },
  langToLocaleMapping,
};

const getLangFn = ({ cachedLang, browserLang, defaultLang }: GetLangParams): string => {
  if (cachedLang) {
    return cachedLang;
  }
  if (browserLang && availableAppLangs.includes(browserLang.toLowerCase())) {
    return browserLang;
  }
  return defaultLang;
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' })),
    provideClientHydration(withEventReplay()),
    provideHttpClient(),
    provideTransloco({ config: translocoConfig(), loader: HttpTranslocoLoader }),
    provideTranslocoPersistLang({ getLangFn, storage: { useValue: cookiesStorage(720 * 3 /* 3 months storage in hours */) } }),
    provideTranslocoLocale(localeConfig),
    { provide: APP_CONFIG, useValue: environment },
  ],
};
