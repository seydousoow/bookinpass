import { InjectionToken } from '@angular/core';

export interface AppEnvironment {
  readonly production: boolean;
  /** Base de l'API Book In Pass. Le front ne connaît aucune autre origine. */
  readonly apiBaseUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppEnvironment>('APP_CONFIG');
