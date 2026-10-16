import { Component, computed, input, output } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { IStayRate, IStayRoom } from '@shared/models';
import { StayPhoto } from '../../ui/stay-photo';
import { RateOption } from '../rate-option/rate-option';

/** Une chambre, ses lits et ses tarifs, triés du moins cher au plus cher. */
@Component({
  selector: 'bip-room-card',
  imports: [TranslocoDirective, StayPhoto, RateOption],
  template: `
    <article *transloco="let t; prefix: 'stay.room'"
             class="rounded-card border-border-subtle bg-surface-muted grid gap-3 border p-3 md:grid-cols-[12rem_1fr]">
      <div class="space-y-2">
        <bip-stay-photo [alt]="room().name" [url]="room().photos?.[0]?.url" class="rounded-card aspect-video" />
        <h3 class="font-bold">{{ room().name }}</h3>
        @if (room().beds?.length) {
          <ul class="text-muted text-sm">
            @for (bed of room().beds; track bed.type) {
              <li>{{ t('beds.' + bed.type, { count: bed.count }) }}</li>
            }
          </ul>
        }
      </div>
      <div [attr.aria-label]="t('rates-of', { name: room().name })" class="space-y-2" role="radiogroup">
        @for (rate of rates(); track rate.id) {
          <bip-rate-option (chosen)="chosen.emit(rate)" [name]="'rates-' + index()" [rate]="rate" [selected]="rate.id === selectedId()" />
        }
      </div>
    </article>
  `,
})
export class RoomCard {
  readonly room = input.required<IStayRoom>();
  readonly index = input.required<number>();
  readonly selectedId = input<string | null>(null);
  readonly chosen = output<IStayRate>();
  protected readonly rates = computed(() => [...this.room().rates].sort((a, b) => Number(a.total_amount) - Number(b.total_amount)));
}
