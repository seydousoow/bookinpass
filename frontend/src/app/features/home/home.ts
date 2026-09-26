import { Component, computed, effect, inject, signal } from '@angular/core';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { SelectOption } from '@shared/models/select.model';
import { CABIN_CLASSES, IFlightSearch, TCabinClass, TTripType } from '@shared/models/search.model';
import { SearchStore } from '@core/search/search.store';
import {
  Button,
  Card,
  DatePicker,
  DatePickerMode,
  fromIso,
  isAfterDay,
  isBeforeDay,
  isValidPassengerCount,
  LocationField,
  PassengerSelector,
  RadioGroup,
  Select,
  todayIso,
  toIso,
} from '@shared/ui';
import { form, FormField, FormRoot, required, validate } from '@angular/forms/signals';
import { Router } from '@angular/router';

@Component({
  selector: 'bip-home-page',
  imports: [TranslocoDirective, DatePicker, PassengerSelector, RadioGroup, Select, Button, Card, FormField, LocationField, FormRoot],
  templateUrl: './home.html',
})
export class Home {
  protected readonly minSearchDate = todayIso();
  protected readonly tripTypes: readonly SelectOption<TTripType>[] = [
    { label: 'search.round-trip', value: 'round' },
    { label: 'search.one-way', value: 'oneway' },
  ];
  private readonly search = signal<IFlightSearch>({
    tripType: 'round',
    origin: '',
    destination: '',
    cabin: ['economy'],
    dates: { start: null, end: null },
    passengers: { adults: 1, children: 0, infants: 0 },
  });
  readonly maxSearchDate = computed(() => {
    const start = this.search().dates.start;
    const date = start ? fromIso(start) : new Date();
    date.setFullYear(date.getFullYear() + 1);
    return toIso(date);
  });
  protected readonly searchForm = form<IFlightSearch>(
    this.search,
    (path) => {
      required(path.tripType, { message: 'common.required-field' });
      required(path.origin, { message: 'common.required-field' });
      required(path.destination, { message: 'common.required-field' });

      required(path.cabin, { message: 'common.required-field' });

      validate(path.destination, ({ value, valueOf }) => {
        const origin = valueOf(path.origin);
        const destination = value();
        if (origin !== null && origin.length > 0 && destination.length > 0 && Object.is(origin, destination)) {
          return { kind: 'similarLocation', message: 'search.location.similar-location' };
        }
        return null;
      });

      validate(path.dates, ({ value, valueOf }) => {
        const tripType = valueOf(path.tripType);
        const { start, end } = value();
        if (!start) {
          return { kind: 'requiredStart', message: 'search.location.date.start-required' };
        }
        if (isBeforeDay(start, todayIso())) {
          return { kind: 'startMinDate', message: 'search.location.date.start-past' };
        }
        if (tripType === 'oneway') {
          return null;
        }
        if (!end) {
          return { kind: 'requiredEnd', message: 'search.location.date.end-required' };
        }
        if (!isAfterDay(end, start)) {
          return { kind: 'endMinDate', message: 'search.location.date.end-past' };
        }
        return null;
      });

      validate(path.passengers, ({ value }) => {
        if (!isValidPassengerCount(value())) {
          return { kind: 'passenger-count', message: 'search.passenger.count' };
        }
        return null;
      });
    },
    {
      submission: {
        action: (f) => {
          this.submitForm(f().value());
          return Promise.resolve(null);
        },
        ignoreValidators: 'none',
        onInvalid: (field) => {
          const firstError = field().errorSummary()[0];
          firstError?.fieldTree().focusBoundControl();
        },
      },
    },
  );
  private readonly transloco = inject(TranslocoService);
  protected readonly cabins: readonly SelectOption<TCabinClass>[] = CABIN_CLASSES.map((value) => ({
    label: this.transloco.translate(`search.cabin.${value}`), value,
  }));
  private readonly router = inject(Router);
  private readonly searchStore = inject(SearchStore);
  private readonly restoredFromStorage = signal(false);

  constructor() {
    // Pré-remplissage depuis la dernière recherche validée (plan §10.3 : après
    // le rendu, jamais pendant l'hydratation) — une seule fois, pour ne pas
    // écraser une saisie en cours si le store change ensuite.
    effect(() => {
      const last = this.searchStore.lastSearch();
      if (!last || this.restoredFromStorage()) {
        return;
      }
      this.restoredFromStorage.set(true);
      this.search.update((old) => ({
        tripType: last.tripType ?? old.tripType,
        origin: last.origin ?? old.origin,
        destination: last.destination ?? old.destination,
        dates: last.dates ?? old.dates,
        passengers: last.passengers ?? old.passengers,
        cabin: [last.cabin ?? old.cabin[0]],
      }));
      console.log(this.search());
    });
  }

  get pickerLabel(): string {
    const dep = this.transloco.translate('search.depart');
    if (this.search().tripType === 'oneway') {
      return dep;
    }
    return `${dep} — ${this.transloco.translate('search.return')}`;
  }

  get pickerMode(): DatePickerMode {
    return this.search().tripType === 'round' ? 'range' : 'single';
  }

  get visibleMonth(): number {
    //todo: check mobile rendering for 2. Restrict to 1 if needed
    return this.search().tripType === 'round' ? 2 : 1;
  }

  private submitForm({
    cabin,
    dates: { end, start },
    destination: d,
    origin: o,
    passengers: { adults, children, infants },
    tripType: t,
  }: IFlightSearch) {
    const queryParams: Record<string, string | number> = { t, o, d, from: String(start) };
    if (end) {
      queryParams['to'] = String(end);
    }
    queryParams['cabin'] = cabin[0];
    queryParams['adt'] = adults;
    if (infants > 0) {
      queryParams['inf'] = infants;
    }
    if (children > 0) {
      queryParams['chd'] = children;
    }
    void this.router.navigate(['/', 'search'], { queryParams });
  }
}
