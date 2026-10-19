import { InjectionToken } from '@angular/core';

export interface AppEnvironment {
  readonly production: boolean;
  /** Base de l'API Book In Pass. Le front ne connaît aucune autre origine. */
  readonly apiBaseUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppEnvironment>('APP_CONFIG');

/**
 * Configuration du build, complétée par celle du déploiement (`window.__env`, posée par `/env.js`) : une même image
 * sert plusieurs environnements. Une valeur absente ou vide garde celle du build.
 */
export function runtimeConfig(build: AppEnvironment, runtime: unknown = (globalThis as { __env?: unknown }).__env): AppEnvironment {
  const apiBaseUrl = (runtime as { apiBaseUrl?: unknown } | undefined)?.apiBaseUrl;
  return typeof apiBaseUrl === 'string' && apiBaseUrl.trim() ? { ...build, apiBaseUrl: apiBaseUrl.trim().replace(/\/+$/, '') } : build;
}
