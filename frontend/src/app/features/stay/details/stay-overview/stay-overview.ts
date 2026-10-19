import { Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroClock } from '@ng-icons/heroicons/outline';
import { IAccommodation } from '@shared/models';
import { AmenityList } from '../../ui/amenity-list';

/** Présentation, horaires d'arrivée et de départ, remise des clés et équipements. */
@Component({
  selector: 'bip-stay-overview',
  imports: [TranslocoDirective, NgIcon, AmenityList],
  providers: [provideIcons({ heroClock })],
  template: `
    <section *transloco="let t; prefix: 'stay.overview'" class="space-y-4">
      <h2 class="text-xl font-bold">{{ t('title') }}</h2>
      @if (accommodation().description; as description) {
        <p class="text-sm leading-relaxed">{{ description }}</p>
      }

      @if (accommodation().check_in_information; as info) {
        <dl class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
          @if (info.check_in_after_time) {
            <div class="flex items-start gap-2">
              <ng-icon aria-hidden="true" class="mt-0.5" name="heroClock" />
              <div>
                <dt class="font-semibold">{{ t('check-in') }}</dt>
                <dd>
                  {{
                    info.check_in_before_time
                      ? t('between', { from: info.check_in_after_time, to: info.check_in_before_time })
                      : t('from', { time: info.check_in_after_time })
                  }}
                </dd>
              </div>
            </div>
          }
          @if (info.check_out_before_time) {
            <div class="flex items-start gap-2">
              <ng-icon aria-hidden="true" class="mt-0.5" name="heroClock" />
              <div>
                <dt class="font-semibold">{{ t('check-out') }}</dt>
                <dd>{{ t('until', { time: info.check_out_before_time }) }}</dd>
              </div>
            </div>
          }
        </dl>
        @if (info.check_in_before_time) {
          <p class="text-muted text-xs">{{ t('late-arrival') }}</p>
        }
      }

      <div>
        <h3 class="mb-2 font-semibold">{{ t('amenities') }}</h3>
        @if (accommodation().amenities?.length) {
          <bip-amenity-list [amenities]="accommodation().amenities" />
        } @else {
          <p class="text-muted text-sm">{{ t('no-amenities') }}</p>
        }
      </div>
    </section>
  `,
})
export class StayOverview {
  readonly accommodation = input.required<IAccommodation>();
}
