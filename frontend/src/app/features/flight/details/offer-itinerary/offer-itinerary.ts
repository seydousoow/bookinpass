import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NgOptimizedImage } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroDocumentText } from '@ng-icons/heroicons/outline';
import { Button, formatDuration } from '@shared/ui';
import { IFlightOfferDetails, IFlightSegment } from '@shared/models';
import { OfferConditions } from '../offer-conditions/offer-conditions';

/** Résumé du vol : compagnie vendeuse, chaque segment avec son appareil, et accès aux conditions du billet. */
@Component({
  selector: 'bip-offer-itinerary',
  templateUrl: './offer-itinerary.html',
  imports: [NgOptimizedImage, TranslocoDirective, NgIcon, Button, OfferConditions],
  providers: [provideIcons({ heroDocumentText })],
})
export class OfferItinerary {
  readonly offer = input.required<IFlightOfferDetails>();
  protected readonly conditionsOpened = signal(false);
  /** Logo complet (symbole + nom) de préférence ; à défaut le symbole seul. */
  protected readonly ownerLogo = computed(() => this.offer().owner.logo_lockup_url ?? this.offer().owner.logo_symbol_url);
  private readonly localeService = inject(TranslocoLocaleService);

  private readonly locale = toSignal(this.localeService.localeChanges$, { initialValue: this.localeService.getLocale() });
  protected readonly slices = computed(() => {
    const day = new Intl.DateTimeFormat(this.locale(), { weekday: 'short', day: 'numeric', month: 'short' });
    const slices = this.offer().slices;
    return slices.map((slice, i) => ({
      id: slice.id,
      // Aller-retour : « Aller » puis « Retour » ; au-delà (multi-destinations), le nom de la ville.
      label: slices.length === 2 ? (i === 0 ? 'search.depart' : 'search.return') : null,
      city: slice.destination.city_name,
      date: slice.segments[0] ? day.format(new Date(slice.segments[0].departing_at)) : '',
      duration: formatDuration(slice.duration),
      brand: slice.fare_brand_name,
      segments: slice.segments,
    }));
  });

  /** Heures locales des aéroports : Duffel les donne sans fuseau, on les affiche telles quelles. */
  protected time(value: string): string {
    return value.slice(11, 16);
  }

  protected logo(segment: IFlightSegment): string | null {
    return segment.marketing_carrier.logo_symbol_url ?? segment.marketing_carrier.logo_lockup_url;
  }

  protected operatedByOther(segment: IFlightSegment): boolean {
    return segment.operating_carrier.id !== segment.marketing_carrier.id;
  }
}
