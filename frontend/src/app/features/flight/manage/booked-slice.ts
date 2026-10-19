import { Component, computed, input, signal } from '@angular/core';
import { DatePipe, NgOptimizedImage } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroChevronDown } from '@ng-icons/heroicons/outline';
import { Badge, BadgeTone, formatDuration } from '@shared/ui';
import { FlightSegmentComponent } from '../search/offer-summary/flight-segment';
import { IBookedSlice } from './manage-booking.service';
import { carriersOf, cityOf, stopsOf } from './booking-view.util';

export type TSliceStatus = 'confirmed' | 'changed' | 'cancelled';

const TONES: Record<TSliceStatus, BadgeTone> = { confirmed: 'success', changed: 'warning', cancelled: 'error' };

/**
 * Un trajet de la réservation : « 1. Vol », villes et date, cabine, frise départ → escales → arrivée avec les
 * compagnies de chaque tronçon, durée, puis le détail des segments à la demande.
 */
@Component({
  selector: 'bip-booked-slice',
  imports: [TranslocoDirective, DatePipe, NgOptimizedImage, NgIcon, Badge, FlightSegmentComponent],
  providers: [provideIcons({ heroChevronDown })],
  templateUrl: './booked-slice.html',
})
export class BookedSlice {
  readonly slice = input.required<IBookedSlice>();
  readonly position = input.required<number>();
  readonly status = input<TSliceStatus>('confirmed');

  protected readonly open = signal(false);
  protected readonly first = computed(() => this.slice().segments[0]);
  protected readonly last = computed(() => this.slice().segments.at(-1)!);
  protected readonly stops = computed(() => stopsOf(this.slice()));
  protected readonly carriers = computed(() => carriersOf(this.slice()));
  /** Cabine du premier passager sur le premier segment (`Economy`), et la marque tarifaire si Duffel la donne. */
  protected readonly cabin = computed(() => this.first().passengers[0]?.cabin_class_marketing_name ?? null);
  protected readonly tone = computed(() => TONES[this.status()]);
  protected readonly duration = computed(() => formatDuration(this.slice().duration));
  protected readonly cityOf = cityOf;
}
