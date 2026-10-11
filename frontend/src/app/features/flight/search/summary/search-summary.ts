import { Component, computed, effect, inject, input, model } from '@angular/core';
import { provideIcons } from '@ng-icons/core';
import { IDateRangeValue, IFlightSearch, normalizeCabin, IPersistedFlightSearch, TTripType } from '@shared/models';
import { SearchStore } from '@core/search/search.store';
import { IsoDate, isValidPassengerCount, IPassengerCount } from '@shared/ui';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';

@Component({
  selector: 'bip-search-summary',
  templateUrl: './search-summary.html',
  imports: [],
  host: { class: 'h-12 bg-red border rounded p-2 border-brand-soft' },
  providers: provideIcons({}),
})
export class SearchSummary implements FormValueControl<IFlightSearch> {
  readonly value = model.required<IFlightSearch>();
  readonly invalid = model(false);

  readonly name = model('');
  readonly touched = model(false);
  readonly dirty = model(false);
  readonly required = model(false);
  readonly disabled = model(false);
  readonly errors = model<readonly WithOptionalFieldTree<ValidationError>[]>([]);

  readonly tripType = input<TTripType | undefined>(undefined);
  readonly origin = input<string | undefined>(undefined);
  readonly destination = input<string | undefined>(undefined);
  readonly from = input<string | undefined>(undefined);
  readonly to = input<string | undefined>(undefined);
  readonly adults = input(1);
  readonly infants = input(0);
  readonly children = input(0);
  readonly cabin = input('economy');

  private readonly urlSearch = computed<IFlightSearch | null>(() => {
    const tripType = this.tripType();
    const origin = this.origin();
    const destination = this.destination();
    const from = this.from();
    const to = this.to();
    const adults = this.adults();
    const children = this.children();
    const infants = this.infants();

    if (!tripType || !origin || !destination || !from || !isValidPassengerCount({ adults, children, infants } as IPassengerCount)) {
      return null;
    }

    if (tripType !== 'oneway' && tripType !== 'round') {
      return null;
    }

    if (tripType === 'round' && !to) {
      return null;
    }

    const cabin = normalizeCabin(this.cabin());
    const dates: IDateRangeValue = { start: from as IsoDate, end: tripType === 'round' ? (to! as IsoDate) : null };
    return { tripType, origin, destination, dates, passengers: { adults, children, infants }, cabin: [cabin] };
  });

  private readonly searchStore = inject(SearchStore);

  constructor() {
    effect(() => {
      const search = this.urlSearch();

      if (!search) {
        this.invalid.set(true);
        return;
      }

      this.invalid.set(false);
      this.value.set(search);

      this.searchStore.save({ ...search, cabin: search.cabin[0] } satisfies IPersistedFlightSearch);
    });
  }

  reset(): void {
    // The URL is the source of truth, so there is nothing to reset here.
  }
}
