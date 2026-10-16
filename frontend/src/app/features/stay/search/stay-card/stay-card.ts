import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMapPin } from '@ng-icons/heroicons/outline';
import { IStayResult } from '@shared/models';
import { Button } from '@shared/ui';
import { nightsBetween } from '../stay-search-params';
import { StarRating } from '../../ui/star-rating';
import { ReviewScore } from '../../ui/review-score';
import { StayPhoto } from '../../ui/stay-photo';
import { AmenityList } from '../../ui/amenity-list';
import { MoneyPipe } from '@core/money/money.pipe';

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
  protected readonly perNight = computed(() => Number(this.result().cheapest_rate_total_amount) / Math.max(1, this.nights()));
  /** Prix public plus élevé : la remise est montrée, prix barré. */
  protected readonly publicPrice = computed(() => {
    const { cheapest_rate_public_amount: pub, cheapest_rate_total_amount: total } = this.result();
    return pub && Number(pub) > Number(total) ? pub : null;
  });
}
