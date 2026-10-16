import { CONTRACT_CURRENCY, fractionDigits } from '@shared/models/money.model';

/**
 * Formatage monétaire — un seul utilitaire, testé une fois (plan §12.2).
 * Le nombre de décimales suit la devise : zéro pour le XOF, deux pour l'euro.
 */
const formatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(amount: number, currency: string, locale = 'fr-SN', display: 'symbol' | 'narrowSymbol' = 'symbol'): string {
  const digits = fractionDigits(currency);
  const key = `${locale}:${currency}:${display}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      currencyDisplay: display,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    });
    formatters.set(key, formatter);
  }
  return formatter.format(digits === 0 ? Math.round(amount) : amount);
}

/** Montant contractuel : XOF, zéro décimale. */
export function formatXof(amount: number, locale = 'fr-SN'): string {
  return formatMoney(amount, CONTRACT_CURRENCY, locale);
}
