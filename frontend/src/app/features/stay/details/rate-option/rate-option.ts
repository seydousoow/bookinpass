import { Component, computed, input, output, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { DatePipe } from '@core/i18n/date.pipe';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheck, heroChevronDown } from '@ng-icons/heroicons/outline';
import { IStayRate } from '@shared/models/stay.model';
import { cancellationPolicy } from '../rate.util';
import { MoneyPipe } from '@core/money/money.pipe';
import { staySellingTotal } from '@core/money/pricing';

/** Stock affiché à partir de ce seuil : « Plus que 2 chambres ». */
const LOW_STOCK = 3;

/**
 * Un tarif d'une chambre : repas, annulation, paiement, avantages, conditions et prix du séjour.
 * Les conditions sont obligatoires à l'affichage (Duffel) ; elles peuvent contenir du HTML, montré en texte.
 */
@Component({
  selector: 'bip-rate-option',
  templateUrl: './rate-option.html',
  imports: [MoneyPipe, TranslocoDirective, DatePipe, NgIcon],
  providers: [provideIcons({ heroCheck, heroChevronDown })],
})
export class RateOption {
  readonly rate = input.required<IStayRate>();
  readonly selected = input(false);
  /** Nom du groupe de boutons radio (une chambre). */
  readonly name = input.required<string>();
  readonly chosen = output<void>();

  protected readonly conditionsOpen = signal(false);
  protected readonly deadlineFormat: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
  protected readonly cancellation = computed(() => cancellationPolicy(this.rate()));
  protected readonly lowStock = computed(() => {
    const quantity = this.rate().quantity_available;
    return quantity !== null && quantity <= LOW_STOCK ? quantity : null;
  });
  /** Prix de vente du tarif : prix Duffel + frais de service BIP. */
  protected readonly total = computed(() => staySellingTotal(this.rate().total_amount, this.rate().total_currency));
  protected readonly publicPrice = computed(() => {
    const pub = this.rate().public_amount;
    return pub && Number(pub) > this.total() ? pub : null;
  });

  protected plain(text: string): string {
    return text.replace(/<[^>]*>/g, '');
  }
}
