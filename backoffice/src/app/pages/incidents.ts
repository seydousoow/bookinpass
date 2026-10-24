import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Session } from '@core/session';
import { dakarDateTime } from '@core/dakar';
import { INCIDENT_TYPES, IncidentType, Route } from '@core/models';

/**
 * Incidents des lignes de la gare : ouverts (par défaut) ou tous ; clos par un chef de gare ou un responsable. Les
 * responsables signalent aussi un incident sur une ligne, sans départ (les autres le font depuis le départ).
 */
@Component({
  selector: 'bo-incidents',
  imports: [FormsModule, RouterLink, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <div class="flex flex-wrap items-end gap-3">
        <h1 class="mr-auto text-xl font-bold">{{ t('incidents.title') }}</h1>
        <label class="flex min-h-11 items-center gap-2 text-sm">
          <input [ngModel]="openOnly()" (ngModelChange)="openOnly.set($event)" type="checkbox"/> {{ t('incidents.open-only') }}
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
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }

      @if (session.manager()) {
        <form (ngSubmit)="report()" class="bo-card flex flex-wrap items-end gap-3">
          <label>
            <span class="bo-label">{{ t('incidents.route') }}</span>
            <select [(ngModel)]="routeId" class="bo-input" name="route" required>
              @for (r of routes(); track r.id) {
                <option [ngValue]="r.id">{{ r.origin.place }} → {{ r.destination.place }} ({{ r.origin.name }})</option>
              }
            </select>
          </label>
          <label>
            <span class="bo-label">{{ t('incidents.type') }}</span>
            <select [(ngModel)]="type" class="bo-input" name="type">
              @for (ty of types; track ty) {
                <option [value]="ty">{{ t('incidents.types.' + ty) }}</option>
              }
            </select>
          </label>
          <label class="min-w-64 flex-1">
            <span class="bo-label">{{ t('incidents.description') }}</span>
            <input [(ngModel)]="description" class="bo-input" maxlength="1000" name="description" required/>
          </label>
          <button [disabled]="busy() || !routeId || !description.trim()" class="bo-btn-outline" type="submit">{{ t('incidents.report') }}</button>
        </form>
      }

      @if (list.isLoading()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      } @else if (!list.value()?.length) {
        <p class="bo-card text-muted">{{ t('incidents.empty') }}</p>
      } @else {
        <ul class="grid gap-2">
          @for (i of list.value(); track i.id) {
            <li class="bo-card space-y-1 text-sm">
              <p class="flex flex-wrap justify-between gap-2">
                <span class="font-semibold">{{ t('incidents.types.' + i.type) }} · {{ i.origin }} → {{ i.destination }}</span>
                <span class="text-muted">{{ dateTime(i.reported_at) }} · {{ i.reported_by }}</span>
              </p>
              @if (i.departure_id) {
                <a [routerLink]="['/departures', i.departure_id]" class="text-brand-primary underline">{{ t('incidents.departure', {time: dateTime(i.departs_at)}) }}</a>
              }
              <p>{{ i.description }}</p>
              @if (i.resolved_at) {
                <p class="text-state-success">✓ {{ i.resolution }} ({{ dateTime(i.resolved_at) }})</p>
              } @else if (session.chief()) {
                <form (ngSubmit)="resolve(i.id)" class="flex flex-wrap gap-2">
                  <input [(ngModel)]="resolutions[i.id]" [name]="'resolution-' + i.id" [placeholder]="t('incidents.resolution')" class="bo-input min-w-64 flex-1" maxlength="1000"/>
                  <button [disabled]="busy() || !resolutions[i.id]?.trim()" class="bo-btn-primary" type="submit">{{ t('incidents.resolve') }}</button>
                </form>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
})
export class IncidentsPage {
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  protected readonly openOnly = signal(true);
  protected readonly station = signal<number | null>(null);
  protected readonly stations = toSignal(this.api.stations().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly routes = toSignal(this.session.manager() ? this.api.routes().pipe(catchError(() => of([] as Route[]))) : of([] as Route[]), {
    initialValue: [] as Route[],
  });
  protected readonly list = rxResource({
    params: () => ({ open: this.openOnly(), station: this.station() }),
    stream: ({ params }) => this.api.incidents(params.open, params.station),
  });
  protected readonly types = INCIDENT_TYPES;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resolutions: Record<number, string> = {};
  protected routeId: number | null = null;
  protected type: IncidentType = 'delay';
  protected description = '';
  protected readonly dateTime = dakarDateTime;

  protected report(): void {
    if (!this.routeId) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .reportIncident({ departure_id: null, route_id: this.routeId, type: this.type, description: this.description.trim() })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.description = '';
          this.list.reload();
        },
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }

  protected resolve(id: number): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .resolveIncident(id, this.resolutions[id].trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.list.reload(),
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
