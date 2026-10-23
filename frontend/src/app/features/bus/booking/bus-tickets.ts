import { Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroArrowRight, heroCheckCircle } from '@ng-icons/heroicons/outline';
import { DatePipe } from '@core/i18n/date.pipe';
import { MoneyPipe } from '@core/money/money.pipe';
import { IBusBooking } from '@shared/models/bus.model';

/** Réservation de car et ses billets : trajet, départ (heure de Dakar), gare d'embarquement, un code par passager. */
@Component({
  selector: 'bip-bus-tickets',
  imports: [TranslocoDirective, NgIcon, DatePipe, MoneyPipe],
  providers: [provideIcons({ heroArrowRight, heroCheckCircle })],
  host: { class: 'block' },
  template: `
    @let b = booking();
    <article *transloco="let t; prefix: 'bus.tickets'" class="space-y-4">
      @if (b.status === 'confirmed') {
        <p class="bg-state-success-soft text-state-success rounded-card flex items-center gap-2 p-3 text-sm font-semibold" role="status">
          <ng-icon aria-hidden="true" name="heroCheckCircle" size="1.25rem"/> {{ t('confirmed') }}
        </p>
      } @else {
        <p class="bg-state-warning-soft text-state-warning rounded-card p-3 text-sm font-semibold" role="status">{{ t('status.' + b.status) }}</p>
      }

      <section class="rounded-card border-border-subtle bg-surface-card border p-4">
        <p class="text-muted text-xs">{{ t('reference') }}</p>
        <p class="text-lg font-bold tracking-widest">{{ b.reference }}</p>
        <p class="mt-3 flex flex-wrap items-center gap-1 text-lg font-bold">
          {{ b.origin.place }} <ng-icon aria-hidden="true" class="text-muted" name="heroArrowRight"/> {{ b.destination.place }}
        </p>
        <p class="first-letter:uppercase">{{ b.departs_at | bipDate: day }} · {{ b.departs_at | bipDate: time }}</p>
        @if (b.last_departure_at) {
          <p class="text-muted text-xs">{{ t('continuous', {until: (b.last_departure_at | bipDate: time)}) }}</p>
        }
        <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-muted text-xs">{{ t('boarding') }}</dt>
            <dd class="font-semibold">{{ b.origin.name }}</dd>
            @if (b.origin.address) {
              <dd class="text-muted text-xs">{{ b.origin.address }}</dd>
            }
            @if (b.origin.phone) {
              <dd class="text-xs"><a [href]="'tel:' + b.origin.phone" class="bip-focusable underline">{{ b.origin.phone }}</a></dd>
            }
          </div>
          <div>
            <dt class="text-muted text-xs">{{ t('arrival') }}</dt>
            <dd class="font-semibold">{{ b.destination.name }}</dd>
          </div>
          <div>
            <dt class="text-muted text-xs">{{ t('operator') }}</dt>
            <dd>{{ b.operator }}</dd>
          </div>
          <div>
            <dt class="text-muted text-xs">{{ t('total') }}</dt>
            <dd class="font-semibold">{{ b.total | bipMoney: b.currency }}</dd>
          </div>
        </dl>
      </section>

      <section>
        <h2 class="mb-2 font-bold">{{ t('title') }}</h2>
        <ul class="grid gap-2">
          @for (ticket of b.tickets; track $index) {
            <li class="rounded-card bg-brand-soft flex flex-wrap items-center justify-between gap-2 p-3">
              <span>{{ ticket.given_name }} {{ ticket.family_name }}</span>
              @if (ticket.code) {
                <span [attr.aria-label]="t('code', {code: ticket.code})" class="text-brand-primary font-mono text-lg font-bold tracking-widest">{{ ticket.code }}</span>
              }
            </li>
          }
        </ul>
        @if (b.status === 'confirmed') {
          <p class="text-muted mt-2 text-xs">{{ t('present') }}</p>
        }
      </section>
    </article>
  `,
})
export class BusTickets {
  readonly booking = input.required<IBusBooking>();
  protected readonly time: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' };
  protected readonly day: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' };
}
