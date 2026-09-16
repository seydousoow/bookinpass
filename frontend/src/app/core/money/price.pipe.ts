import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { CONTRACT_CURRENCY } from '@shared/models/money.model';
import { CurrencyStore } from './currency.store';
import { formatMoney } from './money';

/**
 * Affiche un montant exprimé en devise contractuelle (XOF) dans la devise
 * choisie par l'utilisateur.
 *
 * Le tube est IMPUR à dessein : sa sortie dépend de deux états extérieurs à
 * ses arguments — la devise sélectionnée et la locale active. Un tube pur
 * mémoïserait sur le seul montant et n'afficherait jamais le changement de
 * devise. Le coût est contenu par la mémoïsation ci-dessous et par le mode
 * zoneless, où la détection de changements est rare.
 */
@Pipe({ name: 'bipPrice', pure: false })
export class PricePipe implements PipeTransform {
  private readonly currency = inject(CurrencyStore);
  private readonly locale = inject(TranslocoLocaleService);

  private lastKey = '';
  private lastValue = '';

  transform(amountXof: number | null | undefined, currencyOverride?: string): string {
    if (amountXof === null || amountXof === undefined || Number.isNaN(amountXof)) {
      return '';
    }

    const target = currencyOverride ?? this.currency.selected();
    const locale = this.locale.getLocale();
    const key = `${amountXof}|${target}|${locale}`;
    if (key === this.lastKey) {
      return this.lastValue;
    }

    const converted = this.currency.convertFromContract(amountXof, target);
    // Taux indisponible : on montre le montant contractuel plutôt qu'un prix inventé.
    const value =
      converted === null
        ? formatMoney(amountXof, CONTRACT_CURRENCY, locale)
        : formatMoney(converted, target, locale);

    this.lastKey = key;
    this.lastValue = value;
    return value;
  }
}
