import { ApplicationConfig, inject, Injectable, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTransloco, Translation, TranslocoLoader } from '@jsverse/transloco';
import { routes } from './app.routes';
import { APP_CONFIG, runtimeConfig } from '@core/config';
import { credentialsInterceptor, signedOutInterceptor } from '@core/session';
import { savedLang } from '@core/lang';

@Injectable({ providedIn: 'root' })
class TranslationLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string) {
    return this.http.get<Translation>(`/i18n/${lang}.json`);
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([credentialsInterceptor, signedOutInterceptor])),
    { provide: APP_CONFIG, useFactory: () => runtimeConfig() },
    provideTransloco({
      config: { availableLangs: ['fr', 'en'], defaultLang: savedLang(), fallbackLang: 'fr', reRenderOnLangChange: true, prodMode: !isDevMode() },
      loader: TranslationLoader,
    }),
  ],
};
