import { ApplicationConfig, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, TitleStrategy, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { APP_CONFIG, runtimeConfig } from '@core/config/app-config.token';
import { provideI18n } from '@core/i18n/i18n.config';
import { langInterceptor } from '@core/i18n/lang.interceptor';
import { environment } from '@env';
import { routes } from './app.routes';
import { provideNgIconLoader, provideNgIconsConfig, withCaching } from '@ng-icons/core';
import { AppTitleStrategy } from '@core/config/app-title-strategy';

const fetchIcon = (name: string) => {
  const http = inject(HttpClient);
  return http.get(`/svg/${name}.svg`, { responseType: 'text' });
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // `withComponentInputBinding` : l'URL est la source de vérité de toute
    // recherche (plan §5.2), les composants la reçoivent en entrée.
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' })),

    // FetchBackend est le backend par défaut depuis la v22 : `withFetch()` est
    // déprécié et n'a plus rien à ajouter. Langue du site sur chaque appel à l'API.
    provideHttpClient(withInterceptors([langInterceptor])),

    provideI18n(),
    provideNgIconsConfig({ size: '1.2rem' }),
    provideNgIconLoader((name) => fetchIcon(name), withCaching()),

    { provide: APP_CONFIG, useValue: runtimeConfig(environment) },
    { provide: TitleStrategy, useClass: AppTitleStrategy },
  ],
};
