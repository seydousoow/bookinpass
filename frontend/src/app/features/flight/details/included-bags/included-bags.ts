import { Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroNoSymbol } from '@ng-icons/heroicons/outline';
import { TBaggageType } from '@shared/models';

/** Franchise d'un type de bagage ; 0 : non inclus. */
export interface IBagLine {
  readonly type: TBaggageType;
  readonly quantity: number;
}

/** Bagages inclus dans le billet (en soute, cabine), ou leur absence. */
@Component({
  selector: 'bip-included-bags',
  imports: [TranslocoDirective, NgIcon],
  providers: [provideIcons({ heroNoSymbol })],
  template: `
    <ul *transloco="let t" class="mt-2 space-y-3 text-xs">
      @for (bag of bags(); track bag.type) {
        <li class="flex gap-4">
          @if (bag.quantity > 0) {
            <ng-icon [name]="bag.type === 'checked' ? 'suitcase' : 'trolley'" class="mt-0.5 shrink-0" size="1.5rem"/>
            <div>
              <p>{{ t(bag.type === 'checked' ? 'search.offer-summary.baggage.checked' : 'search.offer-summary.baggage.carry-on', {count: bag.quantity}) }}</p>
              <p class="text-state-success">{{ t('common.included') }}</p>
            </div>
          } @else {
            <ng-icon class="text-muted mt-0.5 shrink-0" name="heroNoSymbol" size="1.5rem"/>
            <p class="text-muted">{{ t(bag.type === 'checked' ? 'flight.offer.traveler.no-checked' : 'flight.offer.traveler.no-carry-on') }}</p>
          }
        </li>
      }
    </ul>
  `,
})
export class IncludedBags {
  readonly bags = input.required<readonly IBagLine[]>();
}
