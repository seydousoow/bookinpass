import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgIcon, NgIconStack, provideIcons } from '@ng-icons/core';
import {
  heroBriefcaseSolid,
  heroChevronRightSolid,
  heroLightBulbSolid,
  heroQuestionMarkCircleSolid,
  heroShoppingBagSolid,
} from '@ng-icons/heroicons/solid';
import { Button } from '@shared/ui';
import { FlightOffer, FlightSegment, InboundOfferSlice, OutboundOfferSlice } from '@shared/models/flight.model';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'bip-flight-offer-card',
  templateUrl: './offer-card.html',
  imports: [Button, TranslocoDirective, NgOptimizedImage, NgIcon, NgIconStack],
  providers: [provideIcons({ heroBriefcaseSolid, heroChevronRightSolid, heroQuestionMarkCircleSolid, heroShoppingBagSolid, heroLightBulbSolid })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block w-full p-2 md:pr-3' },
})
export class FlightOfferCard {
  readonly offer = input.required<FlightOffer>();
  readonly selected = output<void>();

  protected readonly outbound = computed(() => this.offer().slices[0]);

  protected readonly inbound = computed(() => {
    const slices = this.offer().slices;
    return slices.length === 2 ? slices[1] : undefined;
  });

  protected readonly outboundFirstSegment = computed(() => this.outbound().segments[0]);

  protected readonly outboundLastSegment = computed(() => {
    const segments = this.outbound().segments;
    return segments[segments.length - 1];
  });

  private readonly translocoLocale = inject(TranslocoLocaleService);
  private readonly transloco = inject(TranslocoService);
  private readonly locale = this.transloco.activeLang;

  protected firstSegment(slice: OutboundOfferSlice | InboundOfferSlice): FlightSegment {
    return slice.segments[0];
  }

  protected lastSegment(slice: OutboundOfferSlice | InboundOfferSlice): FlightSegment {
    return slice.segments[slice.segments.length - 1];
  }

  protected stopsCount(slice: OutboundOfferSlice | InboundOfferSlice): number {
    return slice.segments.reduce((total, segment) => total + segment.stops.length, 0);
  }

  protected formatTime(value: string): string {
    return new Intl.DateTimeFormat(this.locale(), { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));
  }

  protected formatDuration(duration: string): string {
    const match = duration.match(/^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/);
    if (!match) {
      return duration;
    }
    const [, days, hours, minutes] = match;
    const parts: string[] = [];
    if (days) {
      parts.push(`${days} j`);
    }
    if (hours) {
      parts.push(`${hours} h`);
    }
    if (minutes) {
      parts.push(`${minutes} min`);
    }
    return parts.join(' ');
  }

  protected formatPrice(amount: string | number, currency: string): string {
    return this.translocoLocale.localizeNumber(amount, 'currency', this.locale(), {
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: currency === 'XOF' ? 0 : 2,
    });
  }
  protected hasCheckedBaggage(slice: OutboundOfferSlice | InboundOfferSlice): boolean {
    return slice.segments.some((segment) => segment.passengers.some((passenger) => passenger.baggages.some((baggage) => baggage.type === 'checked')));
  }

  protected hasCabinBaggage(slice: OutboundOfferSlice | InboundOfferSlice): boolean {
    return slice.segments.some((segment) =>
      segment.passengers.some((passenger) => passenger.baggages.some((baggage) => baggage.type === 'carry_on')),
    );
  }

  protected checkedBaggageQuantity(slice: OutboundOfferSlice | InboundOfferSlice): number {
    return slice.segments.reduce(
      (total, segment) =>
        total +
        segment.passengers.reduce(
          (passengerTotal, passenger) =>
            passengerTotal +
            passenger.baggages.filter((baggage) => baggage.type === 'checked').reduce((quantity, baggage) => quantity + baggage.quantity, 0),
          0,
        ),
      0,
    );
  }

  protected isChangeable(): boolean {
    return this.offer().conditions.change_before_departure?.allowed ?? false;
  }

  protected isRecommended(): boolean {
    // Recommendation is a UI/business decision.
    // FlightOffer does not contain a `recommended` field.
    return false;
  }

  protected select(): void {
    this.selected.emit();
  }

  protected viewPriceDetails() {
    console.log('price details');
  }
}
