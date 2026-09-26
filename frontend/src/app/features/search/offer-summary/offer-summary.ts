import { Component, computed, effect, inject, input, output, PLATFORM_ID, signal } from '@angular/core';
import { DOCUMENT, isPlatformBrowser, NgOptimizedImage } from '@angular/common';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  heroArrowRight,
  heroArrowsRightLeft,
  heroCalendarDays,
  heroCheckCircle,
  heroClock,
  heroInformationCircle,
  heroPencilSquare,
  heroXMark,
} from '@ng-icons/heroicons/outline';

import { TranslocoDirective } from '@jsverse/transloco';

import { Button, Overlay } from '@shared/ui';
import { Baggage, FlightOffer, FlightSegment, FlightStop } from '@shared/models/flight.model';
import { TranslocoCurrencyPipe, TranslocoLocaleService } from '@jsverse/transloco-locale';

@Component({
  selector: 'bip-offer-summary',
  templateUrl: './offer-summary.html',
  imports: [TranslocoDirective, CdkTrapFocus, NgIcon, Button, Overlay, TranslocoCurrencyPipe, NgOptimizedImage],
  providers: [
    provideIcons({
      heroArrowRight,
      heroArrowsRightLeft,
      heroCalendarDays,
      heroCheckCircle,
      heroClock,
      heroInformationCircle,
      heroPencilSquare,
      heroXMark,
    }),
  ],
  host: {
    '(document:keydown.escape)': 'onEscape()',
  },
})
export class FlightOfferDetails {
  readonly offer = input.required<FlightOffer>();

  readonly closed = output<void>();
  readonly selected = output<void>();

  protected readonly opened = signal(true);
  protected readonly dialogActive = computed(() => this.opened());
  protected readonly outboundSlice = computed(() => this.offer().slices[0]);
  protected readonly inboundSlice = computed(() => (this.offer().slices.length > 1 ? this.offer().slices[1] : undefined));
  protected readonly baggage = computed(() => {
    const passengers = this.offer().passengers;
    const checked = passengers.reduce((total, passenger) => total + this.checkedBaggageQuantity(passenger.baggages), 0);
    const carryOn = passengers.reduce((total, passenger) => total + this.carryOnBaggageQuantity(passenger.baggages), 0);
    return { checked, carryOn };
  });
  protected readonly hasChanges = computed(() => this.offer().conditions.change_before_departure?.allowed === true);
  protected readonly canRefund = computed(() => this.offer().conditions.refund_before_departure?.allowed === true);

  private readonly lang = inject(TranslocoLocaleService);

  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  constructor() {
    effect(() => {
      if (!this.isBrowser) {
        return;
      }
      this.document.body.style.overflow = this.opened() ? 'hidden' : '';
    });
  }

  protected close(): void {
    this.opened.set(false);

    // Allow Angular to remove the dialog before emitting.
    queueMicrotask(() => {
      this.closed.emit();
    });
  }

  protected onEscape(): void {
    if (this.opened()) {
      this.close();
    }
  }

  protected formatTime(value: string): string {
    return this.lang.localizeDate(new Date(value), this.lang.getLocale(), { hour: '2-digit', minute: '2-digit' });
  }

  protected formatDate(value: string): string {
    return this.lang.localizeDate(new Date(value), this.lang.getLocale(), { weekday: 'short', day: 'numeric', month: 'short' });
  }

  protected formatDuration(duration: string): string {
    const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?/);

    if (!match) {
      return duration;
    }

    const hours = Number(match[1] ?? 0);
    const minutes = Number(match[2] ?? 0);

    if (hours === 0) {
      return `${minutes} min`;
    }

    if (minutes === 0) {
      return `${hours} h`;
    }

    return `${hours} h ${minutes.toString().padStart(2, '0')} min`;
  }

  protected segmentLabel(segment: FlightSegment): string {
    return `${segment.marketing_carrier.name} · ${segment.marketing_carrier_flight_number}`;
  }

  protected stopDuration(stop: FlightStop): string {
    return this.formatDuration(stop.duration);
  }

  protected checkedBaggageQuantity(baggage: readonly Baggage[]): number {
    return baggage.filter((baggage) => baggage.type === 'checked').reduce((total, baggage) => total + baggage.quantity, 0);
  }

  protected carryOnBaggageQuantity(baggage: readonly Baggage[]): number {
    return baggage.filter((baggage) => baggage.type === 'carry_on').reduce((total, baggage) => total + baggage.quantity, 0);
  }

  protected airlineLogo(segment: FlightSegment): string {
    return segment.marketing_carrier.logo_symbol_url;
  }

  protected getMaxDigit(total_currency: string | undefined): number {
    return Object.is(total_currency?.toUpperCase(), 'XOF') ? 0 : 2;
  }
}
