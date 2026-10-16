import { Component, computed, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { DatePipe } from '@core/i18n/date.pipe';
import { IStayQuote, IStayResult } from '@shared/models';
import { nightsBetween } from '../../search/stay-search-params';
import { IRateChoice } from '../rate.util';
import { MoneyPipe } from '@core/money/money.pipe';
import { staySellingTotal, stayServiceFee } from '@core/money/pricing';

interface IPriceLine {
  readonly key: 'base' | 'tax' | 'fee' | 'service';
  readonly amount: string | number;
}

/**
 * Récapitulatif du séjour : dates, voyageurs, chambre choisie et détail du prix. Une fois le tarif vérifié,
 * les montants viennent du devis (prix définitif, acompte éventuel) plutôt que du tarif affiché en recherche.
 */
@Component({
  selector: 'bip-stay-price-summary',
  imports: [MoneyPipe, TranslocoDirective, DatePipe],
  templateUrl: './stay-price-summary.html',
})
export class StayPriceSummary {
  readonly result = input.required<IStayResult>();
  readonly choice = input<IRateChoice | undefined>();
  /** Devis du tarif choisi ; `null` tant qu'il n'est pas vérifié. */
  readonly quote = input<IStayQuote | null>(null);

  protected readonly dateFormat: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' };
  protected readonly nights = computed(() => nightsBetween(this.result().check_in_date, this.result().check_out_date));
  protected readonly adults = computed(() => (this.result().guests ?? []).filter((g) => g.type === 'adult').length);
  protected readonly children = computed(() => (this.result().guests ?? []).filter((g) => g.type === 'child').length);
  /** Montants du devis s'il porte sur le tarif choisi, sinon ceux du tarif. */
  protected readonly prices = computed(() => {
    const rate = this.choice()?.rate;
    if (!rate) {
      return null;
    }
    const source = this.quote() ?? rate;
    const lines: IPriceLine[] = [];
    for (const [key, amount] of [
      ['base', source.base_amount],
      ['tax', source.tax_amount],
      ['fee', source.fee_amount],
    ] as const) {
      if (amount && Number(amount) > 0) {
        lines.push({ key, amount });
      }
    }
    // Frais de service BIP, sous les taxes et frais de l'établissement ; le total les comprend.
    lines.push({ key: 'service', amount: stayServiceFee(source.total_amount, source.total_currency) });
    return {
      lines,
      total: staySellingTotal(source.total_amount, source.total_currency),
      currency: source.total_currency,
      due: source.due_at_accommodation_amount,
      dueCurrency: source.due_at_accommodation_currency,
      deposit: this.quote()?.deposit_amount ?? null,
    };
  });
}
