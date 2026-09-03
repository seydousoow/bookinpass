import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { APP_CONFIG } from '@core/config/app-config.token';
import { provideI18n } from '@core/i18n/i18n.config';
import { environment } from '@env';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // `withComponentInputBinding` : l'URL est la source de vérité de toute
    // recherche (plan §5.2), les composants la reçoivent en entrée.
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),

    provideClientHydration(withEventReplay()),

    // `withFetch` : évite le repli XHR au rendu serveur. Le chunk xhr2 reste
    // émis par platform-server, mais il n'est plus chargé à l'exécution.
    provideHttpClient(withFetch()),

    provideI18n(),

    { provide: APP_CONFIG, useValue: environment },
  ],
};
