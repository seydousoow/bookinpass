import { Component, input, model } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroMinus, heroPlus, heroUser } from '@ng-icons/heroicons/outline';
import { MoneyPipe } from '@core/money/money.pipe';
import { IService, TPassengerType } from '@shared/models';
import { IBagLine, IncludedBags } from '../included-bags/included-bags';
import { IExtraBagGroup, TBagSelection } from './extra-bags.util';

/** Un voyageur à l'étape « Options supplémentaires ». */
export interface IExtrasTraveler {
  readonly id: string;
  readonly type: TPassengerType;
  readonly index: number;
  /** Nom saisi à l'étape précédente, s'il l'est. */
  readonly name: string | null;
  readonly extraBags: readonly IExtraBagGroup[];
  readonly everyFlight: readonly IBagLine[];
  readonly perSlice: readonly { readonly destination: string; readonly bags: readonly IBagLine[] }[];
}

/**
 * Étape « Options supplémentaires » : par voyageur, les bagages payants de chaque segment (ou leur absence),
 * puis les bagages inclus. La sélection (service Duffel → quantité) remonte à la page, qui la facture.
 */
@Component({
  selector: 'bip-extra-bags',
  templateUrl: './extra-bags.html',
  imports: [
    TranslocoDirective,
    NgIcon,
    MoneyPipe,
    IncludedBags
  ],
  providers: [provideIcons({ heroUser, heroPlus, heroMinus })],
})
export class ExtraBags {
  readonly travelers = input.required<readonly IExtrasTraveler[]>();
  readonly selection = model<TBagSelection>({});

  protected bagQuantity(service: IService): number {
    return Math.min(this.selection()[service.id] ?? 0, service.maximum_quantity);
  }

  protected setBagQuantity(service: IService, quantity: number): void {
    const next = Math.max(0, Math.min(service.maximum_quantity, quantity));
    this.selection.update((all) => {
      const others = Object.fromEntries(Object.entries(all).filter(([id]) => id !== service.id));
      return next ? { ...others, [service.id]: next } : others;
    });
  }
}
