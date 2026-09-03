import { AppError } from './app-error';

/**
 * Les huit états du cahier §11. Ce n'est pas un composant d'affichage : c'est
 * le TYPE DE RETOUR de toute donnée distante (plan §4.1). Un écran ne manipule
 * pas « des offres », il manipule « un état de chargement d'offres » — AC13
 * devient alors structurellement vrai.
 */
export type SystemStatus =
  | 'idle'
  | 'loading'
  | 'success'
  | 'empty'
  | 'expired'
  | 'partial'
  | 'error'
  | 'offline';

export type SystemState<T> =
  | { readonly status: 'idle' }
  | { readonly status: 'loading' }
  | { readonly status: 'success'; readonly data: T }
  | { readonly status: 'empty' }
  | { readonly status: 'expired'; readonly error?: AppError }
  | { readonly status: 'partial'; readonly data: T; readonly issues: readonly AppError[] }
  | { readonly status: 'error'; readonly error: AppError }
  | { readonly status: 'offline'; readonly cached?: T };

export const idle = (): SystemState<never> => ({ status: 'idle' });
export const loading = (): SystemState<never> => ({ status: 'loading' });
export const empty = (): SystemState<never> => ({ status: 'empty' });
export const success = <T>(data: T): SystemState<T> => ({ status: 'success', data });
export const failure = <T>(error: AppError): SystemState<T> => ({ status: 'error', error });
