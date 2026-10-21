import { Component, computed, input, output } from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheckCircle, heroHeart, heroPencilSquare, heroShare, heroXMark } from '@ng-icons/heroicons/outline';

import { TranslocoDirective } from '@jsverse/transloco';

import { Button } from '@shared/ui/button/button';
import { Overlay } from '@shared/ui/overlay/overlay';
import { lockScrollWhile } from '@shared/ui/scroll-lock/scroll-lock';
import { formatDuration } from '@shared/ui/utils';
import { IFlightOffer, IOfferSlice } from '@shared/models/flight.model';
import { FlightSegmentComponent } from '@features/flight/search/offer-summary/flight-segment';
import { CabinAmenities } from '@features/flight/search/offer-summary/cabin-amenities/cabin-amenities';
import { MoneyPipe } from '@core/money/money.pipe';
import { sellingTotal } from '@core/money/pricing';

interface TBaggageSummary {
  outbound: { checked: number; carryOn: number };
  inbound: { checked: number; carryOn: number } | undefined;
}

@Component({
  selector: 'bip-offer-summary',
  templateUrl: './offer-summary.html',
  imports: [MoneyPipe, TranslocoDirective, CdkTrapFocus, NgIcon, Button, Overlay, FlightSegmentComponent, CabinAmenities],
  providers: [provideIcons({ heroCheckCircle, heroPencilSquare, heroHeart, heroShare, heroXMark })],
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class FlightOfferDetails {
  readonly offer = input.required<IFlightOffer | undefined>();
  readonly opened = computed(() => this.offer() !== undefined);

  readonly closed = output<void>();
  readonly selected = output<void>();

  protected readonly dialogActive = computed(() => this.opened());
  protected readonly outboundSlice = computed(() => this.offer()?.slices[0]);
  protected readonly inboundSlice = computed(() => {
    const offer = this.offer();
    if (!offer) {
      return undefined;
    }
    return offer.slices.length > 1 ? offer.slices[1] : undefined;
  });
  protected readonly baggage = computed((): TBaggageSummary => {
    const slices = this.offer()?.slices;
    return {
      outbound: slices ? this.toBaggage(slices[0]) : { carryOn: 0, checked: 0 },
      inbound: !slices || slices.length === 1 ? undefined : this.toBaggage(slices[1]),
    } as const satisfies TBaggageSummary;
  });
  protected readonly canChange = computed(() => this.offer()?.conditions.change_before_departure?.allowed === true);
  protected readonly changeFee = computed(() => {
    const penalty = this.offer()?.conditions.change_before_departure?.penalty_amount;
    return penalty && !Number.isNaN(penalty) ? Number(penalty) : 0;
  });

  protected readonly canRefund = computed(() => this.offer()?.conditions.refund_before_departure?.allowed === true);
  protected readonly refundFee = computed(() => {
    const penalty = this.offer()?.conditions.refund_before_departure?.penalty_amount;
    return penalty && !Number.isNaN(penalty) ? Number(penalty) : 0;
  });

  constructor() {
    lockScrollWhile(this.opened);
  }

  protected close(): void {
    // Allow Angular to remove the dialog before emitting.
    queueMicrotask(() => this.closed.emit());
  }

  protected onEscape(): void {
    if (this.opened()) {
      this.close();
    }
  }

  protected formatDuration(duration: string): string {
    return formatDuration(duration);
  }

  /** Prix de vente : total Duffel + frais de service BIP. */
  protected total(offer: IFlightOffer): number {
    return sellingTotal(offer);
  }

  private toBaggage(slice: IOfferSlice) {
    return slice.segments
      .flatMap((s) => s.passengers)
      .flatMap((s) => s.baggages)
      .reduce(
        ({ carryOn, checked }, { quantity, type }) => ({
          checked: checked + (type === 'checked' ? quantity : 0),
          carryOn: carryOn + (type === 'carry_on' ? quantity : 0),
        }),
        { carryOn: 0, checked: 0 },
      );
  }
}
