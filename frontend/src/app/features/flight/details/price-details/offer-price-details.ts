import { Component, computed, input, signal } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { IFlightOfferDetails, ISeatChoice, TPassengerType } from '@shared/models';
import { TranslocoDirective } from '@jsverse/transloco';
import { MoneyPipe } from '@core/money/money.pipe';
import { extraBagsAmount, IExtraBagLine } from '../extra-bags/extra-bags.util';
import { ANCILLARY_MARKUP, intendedServicesAmount, roundMoney, sellingTotal, serviceFee } from '@core/money/pricing';

@Component({
  selector: 'bip-offer-price-details',
  templateUrl: './offer-price-details.html',
  imports: [MoneyPipe, NgIcon, TranslocoDirective],
})
export class BipOfferPriceDetails {
  readonly offer = input.required<IFlightOfferDetails>();
  /** Sièges choisis : ajoutés au total quand Duffel les facture dans la devise de l'offre (le cas courant). */
  readonly seats = input<readonly ISeatChoice[]>([]);
  /** Bagages supplémentaires retenus : ajoutés au total, comme les sièges. */
  readonly bags = input<readonly IExtraBagLine[]>([]);
  protected readonly taxesOpened = signal(false);

  /** Nombre de voyageurs par type, dans l'ordre adulte → enfant → bébé. */
  protected readonly passengerGroups = computed(() => {
    const counts = new Map<TPassengerType, number>();
    for (const { type } of this.offer()?.passengers ?? []) {
      const key = type ?? 'adult';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return (['adult', 'child', 'infant_without_seat'] as const)
      .filter((type) => counts.has(type))
      .map((type) => ({ type, count: counts.get(type)! }));
  });

  protected readonly seatsAmount = computed(() => {
    const currency = this.offer().total_currency;
    const seats = this.seats().filter((s) => s.currency === currency);
    return seats.length ? seats.reduce((sum, s) => sum + Number(s.amount), 0) : null;
  });
  protected readonly bagCount = computed(() => this.bags().reduce((sum, line) => sum + line.quantity, 0));
  protected readonly bagsAmount = computed(() => extraBagsAmount(this.bags(), this.offer().total_currency));
  /** Prix Duffel des sièges choisis, que l'offre tarifée compte déjà dans son total et son tarif de base. */
  private readonly seatPrices = computed(() => {
    const currency = this.offer().total_currency;
    return new Map(
      this.seats().map((s) => [s.serviceId, s.duffelAmount ? Number(s.duffelAmount) : roundMoney(Number(s.amount) / (1 + ANCILLARY_MARKUP), currency)]),
    );
  });
  /** Tarif de base du billet seul : Duffel y ajoute les services prévus, affichés (majorés) sur leurs propres lignes. */
  protected readonly baseFare = computed(() => {
    const offer = this.offer();
    return roundMoney(Number(offer.base_amount) - intendedServicesAmount(offer, this.seatPrices()), offer.base_currency);
  });
  /** Frais de service BIP du billet, sous les taxes Duffel. */
  protected readonly serviceFee = computed(() => serviceFee(this.offer(), this.seatPrices()));
  protected readonly total = computed(() => sellingTotal(this.offer(), this.seatPrices()) + (this.seatsAmount() ?? 0) + (this.bagsAmount() ?? 0));
}
