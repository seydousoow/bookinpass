import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { form, FormField, FormRoot, maxLength, pattern, required } from '@angular/forms/signals';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button } from '@shared/ui/button/button';
import { TextInput } from '@shared/ui/input/input';
import { errorCode, ManageBookingService } from './manage-booking.service';

/** Onglet « Gérer mon vol » de l'accueil : PNR + nom de famille, puis la page de la réservation. */
@Component({
  selector: 'bip-manage-booking-form',
  imports: [TranslocoDirective, FormField, FormRoot, TextInput, Button],
  templateUrl: './manage-booking-form.html',
})
export class ManageBookingForm {
  private readonly bookings = inject(ManageBookingService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  /** Clé de traduction de l'erreur du dernier essai. */
  protected readonly error = signal<string | null>(null);
  private readonly value = signal({
    booking_reference: this.bookings.access()?.booking_reference ?? '',
    last_name: this.bookings.access()?.last_name ?? '',
  });
  protected readonly manageForm = form(
    this.value,
    (path) => {
      required(path.booking_reference, { message: 'common.required-field' });
      pattern(path.booking_reference, /^\s*[A-Za-z0-9]{5,13}\s*$/, { message: 'flight.manage.form.reference-invalid' });
      required(path.last_name, { message: 'common.required-field' });
      maxLength(path.last_name, 50);
    },
    {
      name: 'booking',
      submission: {
        action: () => {
          this.submit();
          return Promise.resolve(null);
        },
        ignoreValidators: 'none',
        onInvalid: (field) => field().errorSummary()[0]?.fieldTree().focusBoundControl(),
      },
    },
  );

  private submit(): void {
    if (this.loading()) {
      return;
    }
    const { booking_reference, last_name } = this.value();
    this.loading.set(true);
    this.error.set(null);
    const access = { booking_reference: booking_reference.trim().toUpperCase(), last_name: last_name.trim() };
    this.bookings.lookup(access).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigate(['/flight', 'booking'], { queryParams: { n: access.booking_reference, l: access.last_name } });
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(`flight.manage.errors.${errorCode(error)}`);
      },
    });
  }
}
