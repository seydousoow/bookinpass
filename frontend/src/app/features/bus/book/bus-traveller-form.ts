import { Component, computed, model } from '@angular/core';
import { applyEach, email, form, FormField, maxLength, pattern, required, validate } from '@angular/forms/signals';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroUser } from '@ng-icons/heroicons/outline';
import { TextInput } from '@shared/ui/input/input';
import { isValidPhone, PhoneInput } from '@shared/ui/phone-input/phone-input';
import { IBusTravellerDetails } from './bus-draft';

/** Lettres (accents compris), espaces, apostrophes et traits d'union. */
const NAME_PATTERN = /^[\p{L}' -]+$/u;

/**
 * Nom de chaque passager (imprimé sur son billet) et coordonnées où envoyer les billets.
 * `check()` affiche les erreurs avant de passer au paiement.
 */
@Component({
  selector: 'bip-bus-traveller-form',
  imports: [TranslocoDirective, NgIcon, TextInput, PhoneInput, FormField],
  host: { class: 'block' },
  providers: [provideIcons({ heroUser })],
  template: `
    <form *transloco="let t; prefix: 'bus.travellers'" class="space-y-6" novalidate>
      <section>
        <h2 class="text-xl font-bold">{{ t('title') }}</h2>
        <p class="text-muted mt-1 text-xs">{{ t('subtitle') }}</p>
        <div class="mt-4 grid gap-4">
          @for (passenger of value().passengers; track $index; let i = $index) {
            <section [attr.aria-labelledby]="'bus-passenger-' + i">
              <h3 [id]="'bus-passenger-' + i" class="mb-2 flex items-center gap-2 font-bold">
                <ng-icon aria-hidden="true" name="heroUser" size="1.25rem"/> {{ t('passenger', {index: i + 1}) }}
              </h3>
              <div class="rounded-card border-border-subtle bg-surface-card grid grid-cols-1 gap-4 border p-4 md:grid-cols-2">
                <bip-input [formField]="travellerForm.passengers[i].given_name" autocomplete="given-name" label="bus.travellers.given-name"/>
                <bip-input [formField]="travellerForm.passengers[i].family_name" autocomplete="family-name" label="bus.travellers.family-name"/>
              </div>
            </section>
          }
        </div>
      </section>
      <section>
        <h2 class="text-xl font-bold">{{ t('contact') }}</h2>
        <div class="rounded-card border-border-subtle bg-surface-card mt-4 grid grid-cols-1 gap-4 border p-4 md:grid-cols-2">
          <bip-input [formField]="travellerForm.email" autocomplete="email" hint="bus.travellers.email-hint" label="bus.travellers.email" type="email"/>
          <bip-phone-input [formField]="travellerForm.phone" hint="bus.travellers.phone-hint" label="bus.travellers.phone"/>
        </div>
      </section>
    </form>
  `,
})
export class BusTravellerForm {
  readonly value = model.required<IBusTravellerDetails>();

  protected readonly travellerForm = form(
    this.value,
    (path) => {
      applyEach(path.passengers, (passenger) => {
        required(passenger.given_name, { message: 'common.required-field' });
        required(passenger.family_name, { message: 'common.required-field' });
        pattern(passenger.given_name, NAME_PATTERN, { message: 'bus.travellers.errors.name' });
        pattern(passenger.family_name, NAME_PATTERN, { message: 'bus.travellers.errors.name' });
        maxLength(passenger.given_name, 50, { message: 'bus.travellers.errors.name-length' });
        maxLength(passenger.family_name, 50, { message: 'bus.travellers.errors.name-length' });
      });
      required(path.email, { message: 'common.required-field' });
      email(path.email, { message: 'bus.travellers.errors.email' });
      required(path.phone, { message: 'common.required-field' });
      validate(path.phone, ({ value }) => (!value() || isValidPhone(value()) ? null : { kind: 'phone', message: 'bus.travellers.errors.phone' }));
    },
    { name: 'traveller' },
  );

  readonly valid = computed(() => this.travellerForm().valid());

  /** Affiche toutes les erreurs ; `true` si la saisie est complète. */
  check(): boolean {
    this.travellerForm().markAsTouched();
    return this.travellerForm().valid();
  }
}
