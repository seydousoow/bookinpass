/**
 * Formatage monétaire — plan §12.2 : XOF, zéro décimale, un seul utilitaire,
 * testé une fois. Le taux de conversion est servi par le backend, jamais codé ici.
 */
const formatters = new Map<string, Intl.NumberFormat>();

function formatter(locale: string, currency: string): Intl.NumberFormat {
  const key = `${locale}:${currency}`;
  let cached = formatters.get(key);
  if (!cached) {
    cached = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
    formatters.set(key, cached);
  }
  return cached;
}

export function formatXof(amount: number, locale = 'fr-SN'): string {
  return formatter(locale, 'XOF').format(Math.round(amount));
}
