import { Component, computed, effect, inject, input, linkedSignal, model, untracked } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { TranslocoDirective } from '@jsverse/transloco';
import {
  IFlightOfferDetails,
  IFlightSegment,
  ISeatChoice,
  ISeatElement,
  ISeatMap,
  ISeatMapCabin,
  ISeatMapsResponse,
  ISeatService,
  TPassengerType,
  TSeatMapElement,
} from '@shared/models';
import { APP_CONFIG } from '@core/config/app-config.token';
import { Button, Spinner } from '@shared/ui';
import { MoneyPipe } from '@core/money/money.pipe';
import { ancillaryPrice } from '@core/money/pricing';

/** Voyageur tel que la page l'affiche : nom saisi, sinon « Adulte n° 1 ». */
export interface ISeatTraveler {
  readonly id: string;
  readonly type: TPassengerType;
  /** Rang au sein de son type. */
  readonly index: number;
  readonly name: string | null;
}

/** Largeur commune des sièges et des éléments de calage (rem) : les colonnes restent alignées. */
const SEAT_REM = 2.25;
const GAP_REM = 0.25;
const FIXED_WIDTH: ReadonlySet<TSeatMapElement['type']> = new Set(['seat', 'empty', 'bassinet']);

interface ICabinView {
  readonly cabin: ISeatMapCabin;
  /** Largeur de chaque section (rem), la plus large sur toute la cabine : sièges et installations s'alignent. */
  readonly sectionWidths: readonly number[];
}

/**
 * Choix des sièges, un par voyageur et par vol, sur les plans de cabine Duffel. Facultatif : un voyageur
 * sans siège choisi en reçoit un à l'enregistrement. Les bébés sur les genoux n'ont pas de siège.
 */
@Component({
  selector: 'bip-flight-seat-selection',
  templateUrl: './seat-selection.html',
  imports: [MoneyPipe, TranslocoDirective, Spinner, Button],
})
export class SeatSelectionComponent {
  readonly offer = input.required<IFlightOfferDetails>();
  readonly travelers = input.required<readonly ISeatTraveler[]>();
  /** Sièges retenus, tous vols confondus. */
  readonly selection = model<readonly ISeatChoice[]>([]);

  protected readonly seatRem = SEAT_REM;
  protected readonly gapRem = GAP_REM;
  protected readonly segments = computed((): readonly IFlightSegment[] => this.offer().slices.flatMap((slice) => slice.segments));
  protected readonly currentSegment = linkedSignal<string | undefined>(() => this.segments()[0]?.id);
  /** Les bébés sans siège n'en réservent pas. */
  protected readonly seatedTravelers = computed(() => this.travelers().filter((t) => t.type !== 'infant_without_seat'));
  protected readonly currentTraveler = linkedSignal<string | undefined>(() => this.seatedTravelers()[0]?.id);
  /** Choix du vol affiché, par voyageur. */
  protected readonly segmentChoices = computed(() => {
    const segment = this.currentSegment();
    return new Map(
      this.selection()
        .filter((c) => c.segmentId === segment)
        .map((c) => [c.passengerId, c]),
    );
  });
  /** Qui occupe quel siège sur le vol affiché (`designator` → voyageur). */
  protected readonly occupied = computed(() => new Map([...this.segmentChoices().values()].map((c) => [c.designator, c.passengerId])));
  protected readonly totals = computed(() => {
    const byCurrency = new Map<string, number>();
    for (const choice of this.selection()) {
      byCurrency.set(choice.currency, (byCurrency.get(choice.currency) ?? 0) + Number(choice.amount));
    }
    return [...byCurrency].map(([currency, amount]) => ({ currency, amount }));
  });
  protected readonly cabins = computed((): readonly ICabinView[] =>
    (this.currentMap()?.cabins ?? []).map((cabin) => ({ cabin, sectionWidths: this.sectionWidths(cabin) })),
  );
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly seatMaps = httpResource<ISeatMapsResponse>(
    () => `${this.apiBaseUrl}/flights/offers/${encodeURIComponent(this.offer().id)}/seat-maps`,
  );
  protected readonly loading = this.seatMaps.isLoading;
  private readonly maps = computed((): readonly ISeatMap[] => (this.seatMaps.hasValue() ? this.seatMaps.value().data : []));
  /** Panne (fournisseur, réseau) : distincte d'une compagnie sans choix de siège, qui rend une liste vide. */
  protected readonly failed = computed(() => !!this.seatMaps.error());
  /** Aucun plan : la compagnie ne propose pas de choix de siège. */
  protected readonly unavailable = computed(() => !this.loading() && !this.maps().length);
  private readonly mapBySegment = computed(() => new Map(this.maps().map((map) => [map.segment_id, map])));
  protected readonly currentMap = computed(() => this.mapBySegment().get(this.currentSegment() ?? ''));

  constructor() {
    // Plans reçus : on écarte les choix devenus impossibles (tarif changé, siège parti, brouillon périmé).
    effect(() => {
      const maps = this.mapBySegment();
      if (!this.seatMaps.hasValue()) {
        return;
      }
      untracked(() => {
        const valid = this.selection().filter((choice) => {
          const seat = this.findSeat(maps.get(choice.segmentId), choice.designator);
          return seat?.available_services?.some((s) => s.id === choice.serviceId && s.passenger_id === choice.passengerId);
        });
        if (valid.length !== this.selection().length) {
          this.selection.set(valid);
        }
      });
    });
  }

  protected readonly hasDisclosures = (choice: ISeatChoice): boolean => choice.disclosures.length > 0;

  protected isSeat(element: TSeatMapElement): element is ISeatElement {
    return element.type === 'seat';
  }

  protected isFixed(element: TSeatMapElement): boolean {
    return FIXED_WIDTH.has(element.type);
  }

  /** Service du siège pour le voyageur courant ; `undefined` : pas réservable par lui. */
  protected serviceFor(seat: ISeatElement): ISeatService | undefined {
    const traveler = this.currentTraveler();
    return seat.available_services?.find((s) => s.passenger_id === traveler);
  }

  /** Lettre du siège (`14B` → `B`), affichée sur le siège même : les rangées n'ont pas toujours les mêmes colonnes. */
  protected letter(seat: ISeatElement): string {
    return seat.designator.replace(/^\d+/, '');
  }

  /** Numéro de la rangée, d'après son premier siège ; vide pour les rangées sans siège (office, toilettes…). */
  protected rowNumber(row: ISeatMapCabin['rows'][number]): string {
    const seat = row.sections.flatMap((s) => s.elements).find((e): e is ISeatElement => e.type === 'seat');
    return seat?.designator.match(/^\d+/)?.[0] ?? '';
  }

  protected overWing(cabin: ISeatMapCabin, index: number): boolean {
    return !!cabin.wings && index >= cabin.wings.first_row_index && index <= cabin.wings.last_row_index;
  }

  protected travelerOf(passengerId: string): ISeatTraveler | undefined {
    return this.travelers().find((t) => t.id === passengerId);
  }

  protected segmentLabel(segment: IFlightSegment): string {
    return `${segment.origin.iata_code} → ${segment.destination.iata_code}`;
  }

  protected hasMap(segment: IFlightSegment): boolean {
    return this.mapBySegment().has(segment.id);
  }

  /** Choisit, change ou libère le siège du voyageur courant ; puis passe au voyageur suivant encore sans siège. */
  protected toggle(seat: ISeatElement): void {
    const segmentId = this.currentSegment();
    const passengerId = this.currentTraveler();
    const service = this.serviceFor(seat);
    if (!segmentId || !passengerId) {
      return;
    }
    const holder = this.occupied().get(seat.designator);
    const others = this.selection().filter((c) => !(c.segmentId === segmentId && c.passengerId === passengerId));
    if (holder === passengerId) {
      this.selection.set(others);
      return;
    }
    if (holder || !service) {
      return;
    }
    this.selection.set([
      ...others,
      {
        segmentId,
        passengerId,
        serviceId: service.id,
        designator: seat.designator,
        // Prix de vente : prix Duffel majoré (services annexes).
        amount: String(ancillaryPrice(service.total_amount, service.total_currency)),
        duffelAmount: service.total_amount,
        currency: service.total_currency,
        disclosures: seat.disclosures ?? [],
      },
    ]);
    const next = this.seatedTravelers().find((t) => t.id !== passengerId && !this.segmentChoices().has(t.id));
    if (next) {
      this.currentTraveler.set(next.id);
    }
  }

  protected retry(): void {
    this.seatMaps.reload();
  }

  protected clear(choice: ISeatChoice): void {
    this.selection.update((all) => all.filter((c) => c !== choice));
  }

  /** Prix de vente d'un siège (prix Duffel majoré). */
  protected price(service: { total_amount: string; total_currency: string }): number {
    return ancillaryPrice(service.total_amount, service.total_currency);
  }

  private findSeat(map: ISeatMap | undefined, designator: string): ISeatElement | undefined {
    for (const cabin of map?.cabins ?? []) {
      for (const row of cabin.rows) {
        for (const section of row.sections) {
          const seat = section.elements.find((e): e is ISeatElement => e.type === 'seat' && e.designator === designator);
          if (seat) {
            return seat;
          }
        }
      }
    }
    return undefined;
  }

  private sectionWidths(cabin: ISeatMapCabin): number[] {
    const widths: number[] = [];
    for (const row of cabin.rows) {
      row.sections.forEach((section, i) => {
        const count = section.elements.filter((e) => FIXED_WIDTH.has(e.type)).length;
        const width = count ? count * SEAT_REM + (count - 1) * GAP_REM : SEAT_REM;
        widths[i] = Math.max(widths[i] ?? 0, width);
      });
    }
    return widths;
  }
}
