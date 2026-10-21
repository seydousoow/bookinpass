import { Component, inject, input } from '@angular/core';
import { IFlightSegment } from '@shared/models/flight.model';
import { NgOptimizedImage, TitleCasePipe } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { formatDuration } from '@shared/ui/utils';
import { heroClock } from '@ng-icons/heroicons/outline';

@Component({
  selector: 'bip-flight-segment',
  templateUrl: './flight-segment.html',
  imports: [NgOptimizedImage, TranslocoDirective, NgIcon, TitleCasePipe],
  providers: [provideIcons({ heroClock })],
})
export class FlightSegmentComponent {
  readonly segments = input.required<readonly IFlightSegment[]>();
  private readonly transloco = inject(TranslocoLocaleService);

  protected segmentLabel(segment: IFlightSegment): string {
    return `${segment.marketing_carrier.name} · ${segment.marketing_carrier_flight_number}`;
  }

  protected formatDuration(duration: string): string {
    return formatDuration(duration);
  }

  protected formatDate(value: string): string {
    return this.transloco.localizeDate(new Date(value), this.transloco.getLocale(), { dateStyle: 'full', timeStyle: 'short' });
  }

  protected airlineLogo(segment: IFlightSegment): string {
    return (segment.marketing_carrier.logo_symbol_url ?? segment.marketing_carrier.logo_lockup_url)!;
  }

  protected computeStop(index: number): string {
    const current = this.segments().at(index);
    const next = this.segments().at(index + 1);

    const arrival = current?.arriving_at;
    const departure = next?.departing_at;

    if (!arrival || !departure) {
      return '';
    }

    const durationMs = new Date(departure).getTime() - new Date(arrival).getTime();

    if (durationMs <= 0) {
      return '';
    }

    const totalMinutes = Math.floor(durationMs / 60_000);

    const days = Math.floor(totalMinutes / (24 * 60));
    const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
    const minutes = totalMinutes % 60;

    const parts: string[] = [];

    if (days > 0) {
      parts.push(`${days}d`);
    }

    if (hours > 0) {
      parts.push(`${hours}h`);
    }

    if (minutes > 0) {
      parts.push(`${minutes}min`);
    }

    return parts.join(' ');
  }
}
