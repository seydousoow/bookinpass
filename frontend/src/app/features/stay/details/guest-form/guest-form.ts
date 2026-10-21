import { Component, computed, input, model } from '@angular/core';
import { applyEach, email, form, FormField, maxLength, pattern, required, validate } from '@angular/forms/signals';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroUser } from '@ng-icons/heroicons/outline';
import { TextInput } from '@shared/ui/input/input';
import { Textarea } from '@shared/ui/textarea/textarea';
import { isValidPhone, PhoneInput } from '@shared/ui/phone-input/phone-input';
import { IGuestName, IStayGuestDetails } from '../stay-draft';

/** Libellé d'un voyageur : « Adulte n° 1 », « Enfant n° 1 · 7 ans ». */
export interface IGuestSlot {
  readonly type: 'adult' | 'child';
  readonly index: number;
  readonly age: number | null;
}

/** Lettres (accents compris), espaces, apostrophes et traits d'union. */
const NAME_PATTERN = /^[\p{L}' -]+$/u;

/**
 * Noms des voyageurs, tels qu'ils figurent sur leur pièce d'identité, et coordonnées de contact.
 * La saisie remonte au fil de l'eau (brouillon) ; `check()` affiche les erreurs avant de passer à la suite.
 */
@Component({
  selector: 'bip-guest-form',
  imports: [TranslocoDirective, NgIcon, TextInput, Textarea, FormField, PhoneInput],
  // Bloc : en ligne, le `space-y` de la page ne s'appliquerait pas (pas d'écart avant Retour / Continuer).
  host: { class: 'block' },
  providers: [provideIcons({ heroUser })],
  templateUrl: './guest-form.html',
})
export class GuestForm {
  readonly slots = input.required<readonly IGuestSlot[]>();
  readonly value = model.required<IStayGuestDetails>();

  // Nom racine `guest` : champs `guest.email`, `guest.phone`…, reconnus par la saisie automatique des navigateurs.
  protected readonly guestForm = form(
    this.value,
    (path) => {
      applyEach(path.guests, (guest) => {
        required(guest.given_name, { message: 'common.required-field' });
        required(guest.family_name, { message: 'common.required-field' });
        pattern(guest.given_name, NAME_PATTERN, { message: 'stay.guest-form.errors.name' });
        pattern(guest.family_name, NAME_PATTERN, { message: 'stay.guest-form.errors.name' });
        maxLength(guest.given_name, 50, { message: 'stay.guest-form.errors.name-length' });
        maxLength(guest.family_name, 50, { message: 'stay.guest-form.errors.name-length' });
      });
      required(path.email, { message: 'common.required-field' });
      email(path.email, { message: 'stay.guest-form.errors.email' });
      required(path.phone, { message: 'common.required-field' });
      // Numéro international (E.164) rendu par `bip-phone-input`.
      validate(path.phone, ({ value }) => (!value() || isValidPhone(value()) ? null : { kind: 'phone', message: 'stay.guest-form.errors.phone' }));
      maxLength(path.requests, 500);
    },
    { name: 'guest' },
  );

  /** Saisie complète (voyageurs et coordonnées valides). */
  readonly valid = computed(() => this.guestForm().valid());

  /** Affiche toutes les erreurs ; `true` si la saisie est complète. */
  check(): boolean {
    this.guestForm().markAsTouched();
    return this.guestForm().valid();
  }
}

/** Liste de noms ajustée au nombre de voyageurs (saisies conservées dans l'ordre). */
export function fitGuests(guests: readonly IGuestName[], count: number): IGuestName[] {
  return Array.from({ length: count }, (_, i) => guests[i] ?? { given_name: '', family_name: '' });
}
