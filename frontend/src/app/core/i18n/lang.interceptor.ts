import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';

/**
 * Langue active du site sur chaque appel à l'API (`Accept-Language: fr` ou `en`) : le backend s'en sert pour les
 * e-mails et la langue par défaut des voyageurs. Les autres requêtes (traductions, icônes) n'en ont pas besoin.
 */
export const langInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  if (!req.url.startsWith(apiBaseUrl)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'Accept-Language': inject(TranslocoService).getActiveLang() } }));
};
