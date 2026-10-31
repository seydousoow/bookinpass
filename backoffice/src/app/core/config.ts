import { InjectionToken } from '@angular/core';

export interface AppEnvironment {
  /** Base de l'API Book In Pass (`https://bip-back.dicortex.com/api`, ou `/api` derrière le relais de développement). */
  readonly apiBaseUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppEnvironment>('APP_CONFIG');

/** Configuration du déploiement (`window.__env`, posée par `/env.js`) ; `/api` à défaut. */
export function runtimeConfig(runtime: unknown = (globalThis as { __env?: unknown }).__env): AppEnvironment {
  const apiBaseUrl = (runtime as { apiBaseUrl?: unknown } | undefined)?.apiBaseUrl;
  return { apiBaseUrl: typeof apiBaseUrl === 'string' && apiBaseUrl.trim() ? apiBaseUrl.trim().replace(/\/+$/, '') : '/api' };
}
