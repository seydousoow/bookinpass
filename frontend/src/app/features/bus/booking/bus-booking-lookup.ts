import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button } from '@shared/ui/button/button';
import { TextInput } from '@shared/ui/input/input';
import { Spinner } from '@shared/ui/spinner/spinner';
import { BusService } from '../bus.service';
import { BusTickets } from './bus-tickets';

/**
 * Billets d'une réservation de car, ouverts depuis l'e-mail (`/bus/booking?n=REF&l=Nom`) ou en saisissant la référence
 * et le nom de famille d'un passager.
 */
@Component({
  selector: 'bip-bus-booking-lookup',
  imports: [TranslocoDirective, Button, TextInput, Spinner, BusTickets],
  template: `
    <div *transloco="let t; prefix: 'bus.lookup'" class="mx-auto max-w-2xl px-2 py-6">
      <h1 class="mb-4 text-xl font-bold">{{ t('title') }}</h1>
      @if (lookup(); as query) {
        @if (result.isLoading()) {
          <div class="flex justify-center py-16"><bip-spinner showLabel/></div>
        } @else if (result.hasValue()) {
          <bip-bus-tickets [booking]="result.value()"/>
        } @else {
          <p class="text-state-error mb-4 text-sm" role="alert">{{ t(notFound() ? 'not-found' : 'failed') }}</p>
        }
      }
      @if (!result.hasValue()) {
        <form (submit)="open($event)" class="rounded-card border-border-subtle bg-surface-card grid gap-4 border p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end" novalidate>
          <bip-input [(value)]="reference" autocomplete="off" label="bus.lookup.reference" name="reference"/>
          <bip-input [(value)]="lastName" autocomplete="family-name" label="bus.lookup.last-name" name="last-name"/>
          <button [disabled]="!reference()?.trim() || !lastName()?.trim()" bipButton type="submit" variant="primary">{{ t('submit') }}</button>
        </form>
      }
    </div>
  `,
})
export class BusBookingLookup {
  /** Référence (`n`) et nom de famille (`l`) dans l'URL, comme pour les vols. */
  readonly n = input<string>();
  readonly l = input<string>();

  protected readonly reference = signal<string | null>(null);
  protected readonly lastName = signal<string | null>(null);
  protected readonly lookup = computed(() => (this.n() && this.l() ? { reference: this.n()!, lastName: this.l()! } : null));

  private readonly api = inject(BusService);
  private readonly router = inject(Router);
  protected readonly result = rxResource({
    params: () => this.lookup() ?? undefined,
    stream: ({ params }) => this.api.booking(params.reference, params.lastName),
  });
  protected readonly notFound = computed(() => {
    const error = this.result.error();
    return error instanceof HttpErrorResponse && error.status === HttpStatusCode.NotFound;
  });

  protected open(event: Event): void {
    event.preventDefault();
    const reference = this.reference()?.trim();
    const lastName = this.lastName()?.trim();
    if (reference && lastName) {
      void this.router.navigate([], { queryParams: { n: reference.toUpperCase(), l: lastName } });
    }
  }
}
