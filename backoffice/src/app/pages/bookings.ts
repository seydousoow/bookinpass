import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Session } from '@core/session';
import { dakarDateTime, dakarTime } from '@core/dakar';
import { DeskBooking } from '@core/models';

/**
 * Guichet : retrouver la réservation d'un voyageur (référence, téléphone ou nom), voir ses billets et son paiement,
 * renvoyer l'e-mail des billets (chef de gare, responsable).
 */
@Component({
  selector: 'bo-bookings',
  imports: [FormsModule, RouterLink, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <h1 class="text-xl font-bold">{{ t('bookings.title') }}</h1>
      <form (ngSubmit)="search()" class="bo-card flex flex-wrap items-end gap-2">
        <label class="min-w-64 flex-1">
          <span class="bo-label">{{ t('bookings.query') }}</span>
          <input [(ngModel)]="query" autocomplete="off" class="bo-input" maxlength="50" name="q"/>
        </label>
        <button [disabled]="busy() || query.trim().length < 2" class="bo-btn-primary" type="submit">{{ t('bookings.search') }}</button>
      </form>
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }
      @if (notice(); as text) {
        <p class="bg-state-success-soft text-state-success rounded-card p-3 text-sm" role="status">{{ text }}</p>
      }
      @if (results(); as list) {
        @if (!list.length) {
          <p class="bo-card text-muted">{{ t('bookings.none') }}</p>
        }
        <ul class="grid gap-2">
          @for (b of list; track b.reference) {
            <li class="bo-card space-y-2 text-sm">
              <div class="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p><span class="font-mono text-base font-bold">{{ b.reference }}</span> · {{ b.origin }} → {{ b.destination }}</p>
                  <a [routerLink]="['/departures', b.departure_id]" class="text-brand-primary underline">{{ dateTime(b.departs_at) }}</a>
                  <p class="text-muted">{{ b.email }} · <a [href]="'tel:' + b.phone" class="underline">{{ b.phone }}</a></p>
                </div>
                <div class="text-right">
                  <p [class]="b.status === 'confirmed' ? 'text-state-success' : 'text-state-warning'" class="font-semibold">{{ t('bookings.status.' + b.status) }}</p>
                  <p>{{ b.total }} {{ b.currency }}</p>
                  @if (b.payment_method) {
                    <p class="text-muted text-xs">{{ t('bookings.method.' + b.payment_method) }} · {{ t('bookings.payment.' + b.payment_status) }}</p>
                  }
                </div>
              </div>
              <ul class="border-border-subtle border-t pt-2">
                @for (ticket of b.tickets; track $index) {
                  <li class="flex flex-wrap justify-between gap-2">
                    <span>{{ ticket.given_name }} {{ ticket.family_name }}</span>
                    <span class="font-mono">{{ ticket.code ?? '—' }}</span>
                    <span [class.text-state-success]="ticket.scanned_at">{{ ticket.scanned_at ? '✓ ' + time(ticket.scanned_at) : t('bookings.not-scanned') }}</span>
                  </li>
                }
              </ul>
              @if (session.chief() && b.status === 'confirmed') {
                <button (click)="resend(b)" [disabled]="busy()" class="bo-btn-outline" type="button">{{ t('bookings.resend') }}</button>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
})
export class BookingsPage {
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  protected query = '';
  protected readonly results = signal<DeskBooking[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly dateTime = dakarDateTime;
  protected readonly time = dakarTime;

  protected search(): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api
      .searchBookings(this.query.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (list) => this.results.set(list),
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }

  protected resend(booking: DeskBooking): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .resendTickets(booking.reference)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.notice.set(`${booking.reference} → ${booking.email}`),
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
