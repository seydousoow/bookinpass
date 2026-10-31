import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { StaffApi } from '@core/api';
import { Session } from '@core/session';
import { dakarTime, dakarToday } from '@core/dakar';
import { Departure } from '@core/models';

/** Départs d'un jour au départ de sa gare (responsables : gare au choix, ou toutes). */
@Component({
  selector: 'bo-departures',
  imports: [FormsModule, RouterLink, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <div class="flex flex-wrap items-end gap-3">
        <h1 class="mr-auto text-xl font-bold">{{ t('departures.title') }}</h1>
        <label>
          <span class="bo-label">{{ t('common.date') }}</span>
          <input [ngModel]="date()" (ngModelChange)="date.set($event)" class="bo-input" type="date"/>
        </label>
        @if (session.manager()) {
          <label>
            <span class="bo-label">{{ t('common.station') }}</span>
            <select [ngModel]="station()" (ngModelChange)="station.set($event)" class="bo-input">
              <option [ngValue]="null">{{ t('common.all-stations') }}</option>
              @for (s of stations(); track s.id) {
                <option [ngValue]="s.id">{{ s.name }} ({{ s.place }})</option>
              }
            </select>
          </label>
        }
      </div>
      <p class="text-muted text-xs">{{ t('common.dakar-time') }}</p>

      @if (departures.isLoading()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      } @else if (departures.error()) {
        <p class="text-state-error" role="alert">{{ t('errors.unexpected') }}</p>
      } @else if (!departures.value()?.length) {
        <p class="bo-card text-muted">{{ t('departures.empty') }}</p>
      } @else {
        <ul class="grid gap-2">
          @for (d of departures.value(); track d.id) {
            <li>
              <a [routerLink]="['/departures', d.id]" class="bo-card bo-focusable flex flex-wrap items-center gap-x-6 gap-y-2 hover:border-brand-primary">
                <span class="w-28 text-2xl font-bold">
                  {{ time(d.departs_at) }}
                  @if (d.last_departure_at) {
                    <span class="text-muted block text-xs font-normal">{{ t('departures.until', {time: time(d.last_departure_at)}) }}</span>
                  }
                </span>
                <span class="min-w-48 flex-1">
                  <span class="block font-semibold">{{ d.origin.place }} → {{ d.destination.place }}</span>
                  <span class="text-muted block text-xs">{{ d.origin.name }}</span>
                </span>
                <span class="text-sm">{{ t('departures.passengers', {scanned: d.scanned, total: d.passengers}) }}</span>
                <span class="text-sm">{{ d.capacity === null ? t('departures.continuous') : t('departures.seats', {sold: d.sold, held: d.held, capacity: d.capacity}) }}</span>
                @if (d.luggage) {
                  <span class="text-sm">{{ t('departures.luggage', {count: d.luggage}) }}</span>
                }
                @if (d.open_incidents) {
                  <span class="bg-state-error-soft text-state-error rounded-pill px-2 py-1 text-xs font-semibold">{{ t('departures.incidents', {count: d.open_incidents}) }}</span>
                }
                <span [class]="statusClass(d)" class="rounded-pill px-2 py-1 text-xs font-semibold">{{ t('departures.status.' + status(d)) }}</span>
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
})
export class DeparturesPage {
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  protected readonly date = signal(dakarToday());
  protected readonly station = signal<number | null>(null);
  protected readonly stations = toSignal(this.api.stations().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly departures = rxResource({
    params: () => ({ date: this.date(), station: this.station() }),
    stream: ({ params }) => this.api.departures(params.date, params.station),
  });
  protected readonly time = dakarTime;

  protected status(d: Departure): 'arrived' | 'departed' | 'planned' {
    return d.arrived_at ? 'arrived' : d.departed_at ? 'departed' : 'planned';
  }

  protected statusClass(d: Departure): string {
    return d.arrived_at ? 'bg-state-success-soft text-state-success' : d.departed_at ? 'bg-state-info-soft text-state-info' : 'bg-surface-muted text-muted';
  }
}
