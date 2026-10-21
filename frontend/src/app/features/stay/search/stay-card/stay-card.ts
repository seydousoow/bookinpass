import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPin } from '@ng-icons/heroicons/outline';
import { IStayResult } from '@shared/models/stay.model';
import { Button } from '@shared/ui/button/button';
import { nightsBetween } from '../stay-search-params';
import { StarRating } from '../../ui/star-rating';
import { ReviewScore } from '../../ui/review-score';
import { StayPhoto } from '../../ui/stay-photo';
import { AmenityList } from '../../ui/amenity-list';
import { MoneyPipe } from '@core/money/money.pipe';
import { staySellingTotal } from '@core/money/pricing';

/** Un établissement de la liste : photo, classement, avis, adresse, équipements et prix le plus bas du séjour. */
@Component({
  selector: 'bip-stay-card',
  templateUrl: './stay-card.html',
  imports: [MoneyPipe, TranslocoDirective, NgIcon, Button, StarRating, ReviewScore, StayPhoto, AmenityList],
  providers: [provideIcons({ heroMapPin })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class StayCard {
  readonly result = input.required<IStayResult>();
  readonly selected = output<void>();

  protected readonly accommodation = computed(() => this.result().accommodation);
  protected readonly nights = computed(() => nightsBetween(this.result().check_in_date, this.result().check_out_date));
  /** Prix de vente : tarif Duffel le moins cher + frais de service BIP. */
  protected readonly total = computed(() => staySellingTotal(this.result().cheapest_rate_total_amount, this.result().cheapest_rate_currency));
  protected readonly perNight = computed(() => this.total() / Math.max(1, this.nights()));
  /** Prix public plus élevé que notre prix de vente : la remise est montrée, prix barré. */
  protected readonly publicPrice = computed(() => {
    const pub = this.result().cheapest_rate_public_amount;
    return pub && Number(pub) > this.total() ? pub : null;
  });
}
