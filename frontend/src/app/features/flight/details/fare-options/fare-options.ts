import { Component, computed, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheck, heroMinus } from '@ng-icons/heroicons/outline';
import { Button } from '@shared/ui';
import { IFlightOfferDetails } from '@shared/models';
import { conditionFlag, conditionLabel, offerChangeLabel } from '../offer-conditions/conditions.util';
import { MoneyPipe } from '@core/money/money.pipe';
import { sellingTotal } from '@core/money/pricing';

/** Étape « Type de billet » : l'offre choisie en recherche, puis ses montées en gamme, de la moins chère à la plus chère. */
@Component({
  selector: 'bip-fare-options',
  templateUrl: './fare-options.html',
  imports: [MoneyPipe, NgTemplateOutlet, TranslocoDirective, NgIcon, Button],
  providers: [provideIcons({ heroCheck, heroMinus })],
})
export class FareOptions {
  /** L'offre de départ en premier, les montées en gamme ensuite. */
  readonly options = input.required<readonly IFlightOfferDetails[]>();
  readonly selectedId = input.required<string>();
  /** Identifiant de l'offre en cours de bascule : son bouton tourne, les autres attendent. */
  readonly pendingId = input<string | null>(null);
  readonly selected = output<IFlightOfferDetails>();

  protected readonly cards = computed(() => {
    const options = this.options();
    const reference = options[0] ? sellingTotal(options[0]) : 0;
    return options.map((offer) => {
      const slice = offer.slices[0];
      const bags = slice?.segments[0]?.passengers[0]?.baggages ?? [];
      const count = (type: string) => bags.filter((b) => b.type === type).reduce((sum, b) => sum + b.quantity, 0);
      return {
        offer,
        /** Prix de vente : total Duffel + frais de service BIP. */
        total: sellingTotal(offer),
        name: slice?.fare_brand_name ?? slice?.segments[0]?.passengers[0]?.cabin_class_marketing_name ?? '',
        extra: sellingTotal(offer) - reference,
        checked: count('checked'),
        carryOn: count('carry_on'),
        refund: conditionLabel('refund', offer.conditions?.refund_before_departure),
        change: offerChangeLabel(offer.conditions?.change_before_departure, offer.slices.map((s) => s.conditions?.change_before_departure)),
        seat: conditionFlag(slice?.conditions?.advance_seat_selection),
        boarding: conditionFlag(slice?.conditions?.priority_boarding),
      };
    });
  });

}
