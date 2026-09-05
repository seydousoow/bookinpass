import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { APP_CONFIG } from '@core/config/app-config.token';
import { provideI18n } from '@core/i18n/i18n.config';
import { environment } from '@env';
import { routes } from './app.routes';
import { provideNgIconsConfig } from '@ng-icons/core';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // `withComponentInputBinding` : l'URL est la source de vérité de toute
    // recherche (plan §5.2), les composants la reçoivent en entrée.
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' })),

    provideClientHydration(withEventReplay()),

    // FetchBackend est le backend par défaut depuis la v22 : `withFetch()` est
    // déprécié et n'a plus rien à ajouter.
    provideHttpClient(),

    provideI18n(),
    provideNgIconsConfig({ size: '1.2rem' }),
    { provide: APP_CONFIG, useValue: environment },
  ],
};
