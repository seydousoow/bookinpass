import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button } from '@shared/ui/button/button';
import { DatePicker } from '@shared/ui/date-picker/date-picker';
import { Select } from '@shared/ui/select/select';
import { IsoDate } from '@shared/ui/date-picker/date-utils';
import { IDateRangeValue } from '@shared/models/search.model';
import { ISelectOption } from '@shared/models/select.model';
import { IBusSearch } from '@shared/models/bus.model';
import { BusService } from '../bus.service';
import { BUS_LIMITS, dakarToday, shiftDay, toBusQueryParams } from '../bus-search-params';

/**
 * Recherche de car : départ, arrivée (seulement les lieux desservis depuis le départ), date et passagers.
 * La soumission ouvre `/bus/search` avec les critères dans l'URL.
 */
@Component({
  selector: 'bip-bus-search-form',
  imports: [TranslocoDirective, Select, DatePicker, Button],
  templateUrl: './bus-search-form.html',
})
export class BusSearchForm {
  /** Critères à afficher (page de résultats). */
  readonly initial = input<IBusSearch | null>(null);

  protected readonly minDate = dakarToday() as IsoDate;
  protected readonly maxDate = shiftDay(dakarToday(), BUS_LIMITS.daysAhead) as IsoDate;
  protected readonly places = toSignal(inject(BusService).places().pipe(catchError(() => of(null))), { initialValue: undefined });
  protected readonly from = signal<string[]>([]);
  protected readonly to = signal<string[]>([]);
  protected readonly date = signal<IDateRangeValue>({ start: null, end: null });
  protected readonly passengers = signal<number[]>([1]);
  /** Soumission refusée : les erreurs s'affichent. */
  protected readonly attempted = signal(false);

  protected readonly fromOptions = computed((): ISelectOption[] => (this.places() ?? []).map((place) => ({ label: place.name, value: place.id })));
  protected readonly toOptions = computed((): ISelectOption[] => {
    const all = this.places() ?? [];
    const origin = all.find((place) => place.id === this.from()[0]);
    const names = new Map(all.map((place) => [place.id, place.name]));
    return (origin?.destinations ?? [])
      .map((id) => ({ label: names.get(id) ?? id, value: id }))
      .sort((a, b) => a.label.localeCompare(b.label));
  });
  protected readonly passengerOptions: readonly ISelectOption<number>[] = Array.from({ length: BUS_LIMITS.maxPassengers }, (_, i) => ({
    label: String(i + 1),
    value: i + 1,
  }));
  protected readonly valid = computed(() => !!this.from()[0] && !!this.to()[0] && !!this.date().start);

  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      const initial = this.initial();
      if (initial) {
        untracked(() => {
          this.from.set([initial.from]);
          this.to.set([initial.to]);
          this.date.set({ start: initial.date as IsoDate, end: null });
          this.passengers.set([initial.passengers]);
        });
      }
    });
    // Autre départ : une arrivée qu'il ne dessert pas est effacée.
    effect(() => {
      const destinations = this.toOptions();
      const to = untracked(this.to)[0];
      if (to && this.places() && !destinations.some((option) => option.value === to)) {
        this.to.set([]);
      }
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.valid()) {
      this.attempted.set(true);
      return;
    }
    const search: IBusSearch = { from: this.from()[0], to: this.to()[0], date: this.date().start!, passengers: this.passengers()[0] ?? 1 };
    void this.router.navigate(['/bus', 'search'], { queryParams: toBusQueryParams(search) });
  }
}
