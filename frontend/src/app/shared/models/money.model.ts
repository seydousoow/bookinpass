/**
 * Table de conversion servie par le backend — jamais calculée ni codée en dur
 * dans le front (plan §12.3). `rates` exprime le prix d'UNE unité de `base`
 * dans chaque devise : 1 XOF = 0,00152449 EUR.
 */
export interface IExchangeRates {
  readonly base: string;
  /** Date du taux, affichable au client. */
  readonly updatedAt: string;
  readonly rates: Readonly<Record<string, number>>;
}

export interface ICurrencyOption {
  readonly code: string;
  /** Libellé localisé, dérivé de `Intl.DisplayNames`. */
  readonly label: string;
}

/**
 * Devise contractuelle : c'est le montant en XOF qui fait foi. Les autres
 * devises sont un confort d'affichage (cadrage §11, point 5).
 */
export const CONTRACT_CURRENCY = 'XOF';

/** Devises sans sous-unité : les afficher avec des décimales est une faute. */
const ZERO_DECIMAL = new Set(['XOF', 'XAF', 'JPY', 'KRW', 'CLP', 'ISK', 'VND']);

export function fractionDigits(currency: string): number {
  return ZERO_DECIMAL.has(currency.toUpperCase()) ? 0 : 2;
}
