import { Component, computed, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroArrowsPointingOut, heroBolt, heroWifi } from '@ng-icons/heroicons/outline';
import { ICabinAmenities, IFlightSegment } from '@shared/models';

interface ICabinLine {
  readonly id: string;
  readonly route: string;
  readonly cabin: string;
  readonly fareBasis: string;
  readonly amenities: ICabinAmenities | null;
  /** Espacement des sièges converti en centimètres (Duffel le donne en pouces). */
  readonly pitchCm: number | null;
}

/** Clé i18n sûre pour une valeur Duffel (`n/a`, `free or paid`…). */
const key = (value: string): string => value.toLowerCase().replace(/[^a-z]+/g, '-');

/**
 * Cabine et équipements de chaque vol (siège, Wi-Fi, prise) — ceux du premier voyageur :
 * Duffel les donne par passager, mais ils ne diffèrent pas d'un voyageur à l'autre sur une même offre.
 */
@Component({
  selector: 'bip-cabin-amenities',
  templateUrl: './cabin-amenities.html',
  imports: [TranslocoDirective, NgIcon],
  providers: [provideIcons({ heroArrowsPointingOut, heroBolt, heroWifi })],
})
export class CabinAmenities {
  readonly segments = input.required<readonly IFlightSegment[]>();

  protected readonly key = key;

  protected readonly lines = computed((): ICabinLine[] =>
    this.segments().map((segment) => {
      const passenger = segment.passengers[0];
      const pitch = Number(passenger?.cabin?.amenities?.seat?.pitch);
      return {
        id: segment.id,
        route: `${segment.origin.iata_code} → ${segment.destination.iata_code}`,
        cabin: passenger?.cabin?.marketing_name || passenger?.cabin_class_marketing_name || '',
        fareBasis: passenger?.fare_basis_code ?? '',
        amenities: passenger?.cabin?.amenities ?? null,
        pitchCm: pitch > 0 ? Math.round(pitch * 2.54) : null,
      };
    }),
  );
}
