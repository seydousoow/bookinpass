import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideTransloco, TranslocoConfig } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
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

const translocoConfig = (): Partial<TranslocoConfig> => ({
  availableLangs: [...availableAppLangs],
  defaultLang: 'fr',
  fallbackLang: 'fr',
  reRenderOnLangChange: true,
  prodMode: environment.production,
  flatten: { aot: environment.production },
});

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
    provideTranslocoLocale({
      localeConfig: { global: { date: { dateStyle: 'long', timeStyle: 'long' } } }, langToLocaleMapping: { fr: 'fr-FR', en: 'en-US' }
    }),
    { provide: APP_CONFIG, useValue: environment },
  ],
};
