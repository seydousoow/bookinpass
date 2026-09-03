/**
 * Contrat d'erreur — cahier §11 : un message dit ce qui s'est passé, ce qui
 * reste enregistré et l'action possible. Le type interdit d'en oublier une.
 */
export interface ErrorAction {
  readonly label: string;
  readonly kind: 'retry' | 'back' | 'restart-search' | 'contact-support' | 'none';
}

export interface AppError {
  readonly code: string;
  /** Ce qui s'est passé, en français, sans jargon technique. */
  readonly what: string;
  /** Ce qui reste enregistré côté client ou serveur. */
  readonly preserved: string;
  readonly action: ErrorAction;
}

export function appError(
  code: string,
  what: string,
  preserved: string,
  action: ErrorAction,
): AppError {
  return { code, what, preserved, action };
}
