import { Component, computed, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheckCircle, heroQuestionMarkCircle, heroXCircle } from '@ng-icons/heroicons/outline';
import { Modal } from '@shared/ui';
import { IFlightOfferDetails } from '@shared/models';
import { conditionFlag, conditionLabel, offerChangeLabel } from './conditions.util';
import { MoneyPipe } from '@core/money/money.pipe';

/** Règles du billet : annulation, modification, puis avantages propres à chaque vol. */
@Component({
  selector: 'bip-offer-conditions',
  templateUrl: './offer-conditions.html',
  imports: [MoneyPipe, Modal, NgTemplateOutlet, TranslocoDirective, NgIcon],
  providers: [provideIcons({ heroCheckCircle, heroXCircle, heroQuestionMarkCircle })],
})
export class OfferConditions {
  readonly offer = input.required<IFlightOfferDetails>();
  readonly closed = output<void>();

  protected readonly refund = computed(() => conditionLabel('refund', this.offer().conditions?.refund_before_departure));
  protected readonly change = computed(() =>
    offerChangeLabel(
      this.offer().conditions?.change_before_departure,
      this.offer().slices.map((slice) => slice.conditions?.change_before_departure),
    ),
  );
  protected readonly slices = computed(() =>
    this.offer().slices.map((slice) => ({
      id: slice.id,
      city: slice.destination.city_name,
      brand: slice.fare_brand_name,
      change: conditionLabel('change', slice.conditions?.change_before_departure),
      perks: [
        { key: 'priority-check-in', flag: conditionFlag(slice.conditions?.priority_check_in) },
        { key: 'priority-boarding', flag: conditionFlag(slice.conditions?.priority_boarding) },
        { key: 'seat-selection', flag: conditionFlag(slice.conditions?.advance_seat_selection) },
      ],
    })),
  );

}
