import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Session } from '@core/session';
import { dakarDateTime, dakarToday } from '@core/dakar';
import { Luggage } from '@core/models';

/** Bagages d'un jour (partant de la gare ou y arrivant) ; recherche par code d'étiquette et remise au destinataire. */
@Component({
  selector: 'bo-luggage',
  imports: [FormsModule, NgTemplateOutlet, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <div class="flex flex-wrap items-end gap-3">
        <h1 class="mr-auto text-xl font-bold">{{ t('luggage.title') }}</h1>
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

      <form (ngSubmit)="find()" class="bo-card flex flex-wrap items-end gap-2">
        <label class="flex-1">
          <span class="bo-label">{{ t('luggage.search') }}</span>
          <input [(ngModel)]="code" autocapitalize="characters" class="bo-input font-mono uppercase" maxlength="8" name="code"/>
        </label>
        <button [disabled]="busy() || code.trim().length !== 8" class="bo-btn-outline" type="submit">{{ t('luggage.find') }}</button>
      </form>
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }
      @if (found(); as l) {
        <div class="bo-card border-brand-primary">
          <ng-container *ngTemplateOutlet="item; context: {$implicit: l}"/>
        </div>
      }

      @if (list.isLoading()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      } @else if (!list.value()?.length) {
        <p class="bo-card text-muted">{{ t('luggage.empty') }}</p>
      } @else {
        <ul class="grid gap-2">
          @for (l of list.value(); track l.code) {
            <li class="bo-card"><ng-container *ngTemplateOutlet="item; context: {$implicit: l}"/></li>
          }
        </ul>
      }

      <ng-template #item let-l>
        <div class="flex flex-wrap items-start justify-between gap-3 text-sm">
          <div>
            <p><span class="font-mono text-base font-bold">{{ l.code }}</span> · {{ l.origin }} → {{ l.destination }} · {{ dateTime(l.departs_at) }}</p>
            <p>{{ t('luggage.from') }} {{ l.sender_name }} ({{ l.sender_phone }}) — {{ t('luggage.to') }} {{ l.receiver_name }}
              (<a [href]="'tel:' + l.receiver_phone" class="underline">{{ l.receiver_phone }}</a>)</p>
            <p class="text-muted">{{ l.weight_kg }} kg · {{ l.price }} {{ l.currency }}{{ l.description ? ' · ' + l.description : '' }}</p>
          </div>
          <div class="text-right">
            <p [class.text-state-success]="l.status === 'delivered'" class="font-semibold">{{ t('luggage.status.' + l.status) }}</p>
            @if (l.delivered_at) {
              <p class="text-muted text-xs">{{ dateTime(l.delivered_at) }}</p>
            } @else {
              <button (click)="deliver(l)" [disabled]="busy()" class="bo-btn-primary mt-1" type="button">{{ t('luggage.deliver') }}</button>
            }
          </div>
        </div>
      </ng-template>
    </section>
  `,
})
export class LuggagePage {
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  protected readonly date = signal(dakarToday());
  protected readonly station = signal<number | null>(null);
  protected readonly stations = toSignal(this.api.stations().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly list = rxResource({
    params: () => ({ date: this.date(), station: this.station() }),
    stream: ({ params }) => this.api.luggage(params.date, params.station),
  });
  protected readonly found = signal<Luggage | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected code = '';
  protected readonly dateTime = dakarDateTime;

  protected find(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .findLuggage(this.code.trim().toUpperCase())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (item) => this.found.set(item),
        error: (error: unknown) => {
          this.found.set(null);
          this.error.set(errorCode(error));
        },
      });
  }

  protected deliver(item: Luggage): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .deliverLuggage(item.code)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (delivered) => {
          if (this.found()?.code === delivered.code) {
            this.found.set(delivered);
          }
          this.list.reload();
        },
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
