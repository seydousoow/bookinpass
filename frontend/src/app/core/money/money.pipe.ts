import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { CurrencyStore } from './currency.store';
import { formatMoney } from './money';

/**
 * Montant fournisseur (chaîne décimale Duffel, ex. `"799.00"`) converti dans la devise choisie par l'utilisateur :
 * `{{ offer.total_amount | bipMoney: offer.total_currency }}`. Taux indisponible : le montant reste dans sa devise
 * d'origine plutôt qu'un prix inventé.
 *
 * Impur pour suivre la devise choisie et la langue ; mémoïsé sur ses entrées.
 */
@Pipe({ name: 'bipMoney', pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly currency = inject(CurrencyStore);
  private readonly locale = inject(TranslocoLocaleService);
  private lastKey = '';
  private lastValue = '';

  transform(amount: string | number | null | undefined, from: string | null | undefined): string {
    if (amount === null || amount === undefined || amount === '' || !from || Number.isNaN(Number(amount))) {
      return '';
    }
    const target = this.currency.selected();
    const locale = this.locale.getLocale();
    const converted = this.currency.convert(Number(amount), from, target);
    const key = `${amount}|${from}|${target}|${converted}|${locale}`;
    if (key !== this.lastKey) {
      this.lastKey = key;
      this.lastValue = converted === null ? formatMoney(Number(amount), from, locale, 'narrowSymbol') : formatMoney(converted, target, locale, 'narrowSymbol');
    }
    return this.lastValue;
  }
}
