import { Component, computed, input, linkedSignal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { IBookedOrder, IBookedPassenger, IBookedService } from './manage-booking.service';
import { initials, ticketNumber } from './booking-view.util';

export type TPreferenceTab = 'passengers' | 'seats' | 'bags' | 'meals';

interface IBagLine {
  readonly type: string;
  readonly quantity: number;
  /** Bagage acheté avec la réservation (service), et non inclus dans le tarif. */
  readonly bought: boolean;
  readonly weightKg?: number | null;
}

interface ILegRow {
  /** `JNB → IST` (segment) ou `JNB → CDG` (trajet). */
  readonly route: string;
  readonly cells: readonly { readonly passenger: IBookedPassenger; readonly value: string | null; readonly bags?: readonly IBagLine[] }[];
}

const metadata = (service: IBookedService, key: string): unknown => service.metadata?.[key];

/**
 * Préférences des passagers, en onglets : Passagers (billet, fidélité) toujours, puis Siège, Bagages et Repas
 * seulement si la commande en porte (sièges attribués, bagages inclus ou achetés, repas commandés). Lecture seule :
 * Duffel ne permet pas de les changer après la réservation par cette page.
 */
@Component({
  selector: 'bip-passenger-preferences',
  imports: [TranslocoDirective],
  templateUrl: './passenger-preferences.html',
})
export class PassengerPreferences {
  readonly order = input.required<IBookedOrder>();

  protected readonly initials = initials;
  protected readonly passengers = computed(() =>
    this.order().passengers.map((passenger) => ({ passenger, ticket: ticketNumber(this.order(), passenger.id) })),
  );

  /** Siège de chaque passager sur chaque segment : attribué par la compagnie ou acheté (service `seat`). */
  protected readonly seats = computed((): ILegRow[] => {
    const order = this.order();
    const bought = (order.services ?? []).filter((s) => s.type === 'seat');
    return order.slices.flatMap((slice) =>
      slice.segments.map((segment) => ({
        route: `${segment.origin.iata_code} → ${segment.destination.iata_code}`,
        cells: order.passengers.map((passenger) => {
          const assigned = segment.passengers.find((p) => p.passenger_id === passenger.id)?.seat?.designator;
          const service = bought.find((s) => s.segment_ids.includes(segment.id) && s.passenger_ids.includes(passenger.id));
          const designator = service ? (metadata(service, 'designator') as string | undefined) : undefined;
          return { passenger, value: assigned ?? designator ?? null };
        }),
      })),
    );
  });
  protected readonly hasSeats = computed(() => this.seats().some((row) => row.cells.some((c) => !!c.value)));

  /** Bagages par trajet : franchise du tarif (premier segment) et bagages achetés pour ce trajet. */
  protected readonly bags = computed((): ILegRow[] => {
    const order = this.order();
    const bought = (order.services ?? []).filter((s) => s.type === 'baggage');
    return order.slices.map((slice) => {
      const segmentIds = slice.segments.map((s) => s.id);
      return {
        route: `${slice.origin.iata_code} → ${slice.destination.iata_code}`,
        cells: order.passengers.map((passenger) => {
          const included: IBagLine[] = (slice.segments[0]?.passengers.find((p) => p.passenger_id === passenger.id)?.baggages ?? [])
            .filter((b) => b.quantity > 0)
            .map((b) => ({ type: b.type, quantity: b.quantity, bought: false }));
          const extra: IBagLine[] = bought
            .filter((s) => s.passenger_ids.includes(passenger.id) && s.segment_ids.some((id) => segmentIds.includes(id)))
            .map((s) => ({
              type: (metadata(s, 'type') as string | undefined) ?? 'checked',
              quantity: s.quantity,
              bought: true,
              weightKg: metadata(s, 'maximum_weight_kg') as number | null | undefined,
            }));
          return { passenger, value: null, bags: [...included, ...extra] };
        }),
      };
    });
  });
  protected readonly hasBags = computed(() => this.bags().some((row) => row.cells.some((c) => !!c.bags?.length)));

  /** Repas commandés (service `meal`), par passager. */
  protected readonly meals = computed(() => {
    const order = this.order();
    const meals = (order.services ?? []).filter((s) => s.type === 'meal');
    return order.passengers
      .map((passenger) => ({ passenger, meals: meals.filter((m) => m.passenger_ids.includes(passenger.id)).map((m) => String(metadata(m, 'meal') ?? '')) }))
      .filter((row) => row.meals.length > 0);
  });

  protected readonly tabs = computed((): TPreferenceTab[] => [
    'passengers',
    ...(this.hasSeats() ? (['seats'] as const) : []),
    ...(this.hasBags() ? (['bags'] as const) : []),
    ...(this.meals().length ? (['meals'] as const) : []),
  ]);
  /** Onglet ouvert ; revient à « Passagers » si celui choisi disparaît (réservation relue). */
  protected readonly active = linkedSignal<TPreferenceTab[], TPreferenceTab>({
    source: this.tabs,
    computation: (tabs, previous) => (previous && tabs.includes(previous.value) ? previous.value : 'passengers'),
  });

  protected tabClass(tab: TPreferenceTab): string {
    return [
      'flex-1 cursor-pointer whitespace-nowrap px-4 py-3 text-sm font-semibold transition-colors',
      this.active() === tab ? 'bg-surface-card text-ink' : 'bg-surface-page text-muted hover:text-ink',
    ].join(' ');
  }
}
