import { Component, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, LuggageForm, StaffApi } from '@core/api';
import { Session } from '@core/session';
import { dakarDateTime, dakarTime } from '@core/dakar';
import { DepartureDetail, INCIDENT_TYPES, IncidentType } from '@core/models';

const EMPTY_LUGGAGE: LuggageForm = {
  sender_name: '',
  sender_phone: '+221',
  sender_id_number: null,
  receiver_name: '',
  receiver_phone: '+221',
  description: null,
  weight_kg: 1,
  price: 0,
};

/**
 * Un départ : passagers et contrôle, car parti / arrivé, places (autre car), bagages enregistrés au guichet, incidents.
 * Chaque action renvoie le départ à jour.
 */
@Component({
  selector: 'bo-departure',
  imports: [FormsModule, RouterLink, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <a class="text-brand-primary bo-focusable text-sm font-semibold" routerLink="/">← {{ t('departure.back') }}</a>
      @if (detail.isLoading() && !detail.hasValue()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      } @else if (detail.error()) {
        <p class="text-state-error" role="alert">{{ t('errors.departure-not-found') }}</p>
      } @else if (detail.value(); as v) {
        @let d = v.departure;
        <div class="bo-card flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 class="text-xl font-bold">{{ d.origin.place }} → {{ d.destination.place }}</h1>
            <p>{{ dateTime(d.departs_at) }}
              @if (d.last_departure_at) {
                <span class="text-muted text-sm">({{ t('departures.until', {time: time(d.last_departure_at)}) }})</span>
              }
            </p>
            <p class="text-muted text-sm">{{ d.origin.name }} → {{ d.destination.name }}</p>
            <p class="mt-2 text-sm">{{ t('departures.passengers', {scanned: d.scanned, total: d.passengers}) }}
              · {{ d.capacity === null ? t('departures.continuous') : t('departures.seats', {sold: d.sold, held: d.held, capacity: d.capacity}) }}</p>
            @if (d.departed_at) {
              <p class="text-sm">{{ t('departure.departed-at', {time: dateTime(d.departed_at)}) }}</p>
            }
            @if (d.arrived_at) {
              <p class="text-sm">{{ t('departure.arrived-at', {time: dateTime(d.arrived_at)}) }}</p>
            }
          </div>
          <div class="flex flex-wrap items-end gap-2">
            @if (session.chief() && !d.departed_at) {
              <button (click)="act(api.markDeparted(d.id))" [disabled]="busy()" class="bo-btn-primary" type="button">{{ t('departure.mark-departed') }}</button>
            }
            @if (session.chief() && d.departed_at && !d.arrived_at) {
              <button (click)="act(api.markArrived(d.id))" [disabled]="busy()" class="bo-btn-primary" type="button">{{ t('departure.mark-arrived') }}</button>
            }
            @if (session.manager() && d.capacity !== null) {
              <label>
                <span class="bo-label">{{ t('departure.capacity') }}</span>
                <input [(ngModel)]="capacity" class="bo-input w-24" min="1" type="number"/>
              </label>
              <button (click)="act(api.resize(d.id, capacity))" [disabled]="busy() || !capacity" class="bo-btn-outline" type="button">{{ t('common.save') }}</button>
            }
          </div>
        </div>
        @if (error(); as code) {
          <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
        }

        <section class="bo-card">
          <h2 class="mb-2 font-bold">{{ t('departure.passengers', {count: v.passengers.length}) }}</h2>
          @if (!v.passengers.length) {
            <p class="text-muted text-sm">{{ t('departure.no-passengers') }}</p>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm">
                <thead class="text-muted text-xs">
                  <tr><th class="py-2">{{ t('departure.name') }}</th><th>{{ t('departure.ticket') }}</th><th>{{ t('departure.booking') }}</th><th>{{ t('departure.phone') }}</th><th>{{ t('departure.check') }}</th></tr>
                </thead>
                <tbody>
                  @for (p of v.passengers; track p.code) {
                    <tr class="border-border-subtle border-t">
                      <td class="py-2 font-semibold">{{ p.family_name }} {{ p.given_name }}</td>
                      <td class="font-mono">{{ p.code }}</td>
                      <td class="font-mono">{{ p.reference }}</td>
                      <td><a [href]="'tel:' + p.phone" class="underline">{{ p.phone }}</a></td>
                      <td>
                        @if (p.scanned_at) {
                          <span class="text-state-success font-semibold">✓ {{ time(p.scanned_at) }}</span>
                          <span class="text-muted text-xs"> {{ p.scanned_by }}</span>
                        } @else {
                          <span class="text-muted">—</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        @if (session.chief()) {
          <section class="bo-card space-y-3">
            <h2 class="font-bold">{{ t('departure.luggage', {count: v.luggage.length}) }}</h2>
            @for (l of v.luggage; track l.code) {
              <div class="border-border-subtle flex flex-wrap justify-between gap-2 border-t pt-2 text-sm">
                <span><span class="font-mono font-bold">{{ l.code }}</span> · {{ l.weight_kg }} kg · {{ l.price }} {{ l.currency }}</span>
                <span>{{ l.sender_name }} → {{ l.receiver_name }} ({{ l.receiver_phone }})</span>
                <span [class.text-state-success]="l.status === 'delivered'">{{ t('luggage.status.' + l.status) }}</span>
              </div>
            }
            @if (!d.departed_at) {
              <form (ngSubmit)="registerLuggage(d.id)" class="border-border-subtle grid gap-3 border-t pt-3 sm:grid-cols-2 lg:grid-cols-4">
                <label><span class="bo-label">{{ t('luggage.sender') }}</span><input [(ngModel)]="luggage.sender_name" class="bo-input" name="sn" required/></label>
                <label><span class="bo-label">{{ t('luggage.sender-phone') }}</span><input [(ngModel)]="luggage.sender_phone" class="bo-input" name="sp" required type="tel"/></label>
                <label><span class="bo-label">{{ t('luggage.sender-id') }}</span><input [(ngModel)]="luggage.sender_id_number" class="bo-input" name="si"/></label>
                <label><span class="bo-label">{{ t('luggage.description') }}</span><input [(ngModel)]="luggage.description" class="bo-input" name="de"/></label>
                <label><span class="bo-label">{{ t('luggage.receiver') }}</span><input [(ngModel)]="luggage.receiver_name" class="bo-input" name="rn" required/></label>
                <label><span class="bo-label">{{ t('luggage.receiver-phone') }}</span><input [(ngModel)]="luggage.receiver_phone" class="bo-input" name="rp" required type="tel"/></label>
                <label><span class="bo-label">{{ t('luggage.weight') }}</span><input [(ngModel)]="luggage.weight_kg" class="bo-input" min="0.1" name="w" required step="0.1" type="number"/></label>
                <label><span class="bo-label">{{ t('luggage.price') }}</span><input [(ngModel)]="luggage.price" class="bo-input" min="0" name="p" required step="1" type="number"/></label>
                <div class="sm:col-span-2 lg:col-span-4">
                  <button [disabled]="busy()" class="bo-btn-primary" type="submit">{{ t('luggage.register') }}</button>
                  @if (lastLuggage(); as code) {
                    <span class="ml-3 text-sm">{{ t('luggage.label-code') }} <span class="font-mono text-lg font-bold">{{ code }}</span></span>
                  }
                </div>
              </form>
            }
          </section>
        }

        <section class="bo-card space-y-3">
          <h2 class="font-bold">{{ t('departure.incidents', {count: v.incidents.length}) }}</h2>
          @for (i of v.incidents; track i.id) {
            <div class="border-border-subtle border-t pt-2 text-sm">
              <p><span class="font-semibold">{{ t('incidents.types.' + i.type) }}</span> · {{ dateTime(i.reported_at) }} · {{ i.reported_by }}</p>
              <p>{{ i.description }}</p>
              @if (i.resolved_at) {
                <p class="text-state-success">✓ {{ i.resolution }}</p>
              }
            </div>
          }
          <form (ngSubmit)="report(d.id)" class="border-border-subtle flex flex-wrap items-end gap-3 border-t pt-3">
            <label>
              <span class="bo-label">{{ t('incidents.type') }}</span>
              <select [(ngModel)]="incidentType" class="bo-input" name="type">
                @for (type of types; track type) {
                  <option [value]="type">{{ t('incidents.types.' + type) }}</option>
                }
              </select>
            </label>
            <label class="min-w-64 flex-1">
              <span class="bo-label">{{ t('incidents.description') }}</span>
              <input [(ngModel)]="incidentText" class="bo-input" maxlength="1000" name="text" required/>
            </label>
            <button [disabled]="busy() || !incidentText.trim()" class="bo-btn-outline" type="submit">{{ t('incidents.report') }}</button>
          </form>
        </section>
      }
    </section>
  `,
})
export class DeparturePage {
  readonly id = input.required<string>();
  protected readonly session = inject(Session);
  protected readonly api = inject(StaffApi);
  protected readonly detail = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.departure(params),
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lastLuggage = signal<string | null>(null);
  protected readonly types = INCIDENT_TYPES;
  protected capacity = 0;
  protected luggage: LuggageForm = { ...EMPTY_LUGGAGE };
  protected incidentType: IncidentType = 'delay';
  protected incidentText = '';
  protected readonly time = dakarTime;
  protected readonly dateTime = dakarDateTime;
  constructor() {
    // Champ des places prérempli avec celles du départ chargé.
    effect(() => {
      const capacity = this.detail.value()?.departure.capacity;
      if (capacity !== undefined && capacity !== null) {
        this.capacity = capacity;
      }
    });
  }

  protected act(action: Observable<DepartureDetail>): void {
    this.run(action, (detail) => this.detail.set(detail));
  }

  protected registerLuggage(departureId: number): void {
    this.run(this.api.registerLuggage(departureId, this.luggage), (item) => {
      this.lastLuggage.set(item.code);
      this.luggage = { ...EMPTY_LUGGAGE };
      this.detail.reload();
    });
  }

  protected report(departureId: number): void {
    this.run(this.api.reportIncident({ departure_id: departureId, route_id: null, type: this.incidentType, description: this.incidentText.trim() }), () => {
      this.incidentText = '';
      this.detail.reload();
    });
  }

  private run<T>(action: Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    action.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: done,
      error: (error: unknown) => this.error.set(errorCode(error)),
    });
  }
}
