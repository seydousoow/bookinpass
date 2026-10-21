import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgIcon, NgIconStack, provideIcons } from '@ng-icons/core';
import {
  heroBriefcaseSolid,
  heroChevronRightSolid,
  heroLightBulbSolid,
  heroQuestionMarkCircleSolid,
  heroShoppingBagSolid,
} from '@ng-icons/heroicons/solid';
import { Button } from '@shared/ui/button/button';
import { Viewport } from '@shared/ui/viewport/viewport';
import { IFlightOffer, IFlightSegment, IOfferSlice } from '@shared/models/flight.model';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MoneyPipe } from '@core/money/money.pipe';
import { NgOptimizedImage } from '@angular/common';
import { sellingTotal } from '@core/money/pricing';

@Component({
  selector: 'bip-flight-offer-card',
  templateUrl: './offer-card.html',
  imports: [MoneyPipe, Button, TranslocoDirective, NgOptimizedImage, NgIcon, NgIconStack],
  providers: [provideIcons({ heroBriefcaseSolid, heroChevronRightSolid, heroQuestionMarkCircleSolid, heroShoppingBagSolid, heroLightBulbSolid })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block w-full md:px-2' },
})
export class FlightOfferCard {
  readonly offer = input.required<IFlightOffer>();
  /** Prix de vente : total Duffel + frais de service BIP. */
  protected readonly total = computed(() => sellingTotal(this.offer()));
  readonly selected = output<void>();

  protected readonly outbound = computed(() => this.offer().slices[0]);

  protected readonly inbound = computed(() => {
    const slices = this.offer().slices;
    return slices.length === 2 ? slices[1] : undefined;
  });

  protected readonly outboundFirstSegment = computed(() => this.outbound().segments[0]);

  protected readonly outboundLastSegment = computed(() => {
    const segments = this.outbound().segments;
    return segments.at(-1)!;
  });

  private readonly transloco = inject(TranslocoService);
  private readonly locale = this.transloco.activeLang;
  private readonly viewport = inject(Viewport);

  get isMobile(): boolean {
    return this.viewport.isMobile();
  }

  protected firstSegment(slice: IOfferSlice): IFlightSegment {
    return slice.segments[0];
  }

  protected lastSegment(slice: IOfferSlice): IFlightSegment {
    return slice.segments.at(-1)!;
  }

  protected stopsCount(slice: IOfferSlice): number {
    return slice.segments.reduce((total, segment) => total + segment.stops.length, 0);
  }

  protected formatTime(value: string): string {
    return new Intl.DateTimeFormat(this.locale(), { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));
  }

  protected formatDuration(duration: string): string {
    const match = new RegExp(/^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/).exec(duration);
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

  protected hasCheckedBaggage(slice: IOfferSlice): boolean {
    return slice.segments.some((segment) => segment.passengers.some((passenger) => passenger.baggages.some((baggage) => baggage.type === 'checked')));
  }

  protected hasCabinBaggage(slice: IOfferSlice): boolean {
    return slice.segments.some((segment) =>
      segment.passengers.some((passenger) => passenger.baggages.some((baggage) => baggage.type === 'carry_on')),
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
