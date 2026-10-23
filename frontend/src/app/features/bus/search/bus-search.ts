import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroArrowRight, heroChevronLeft, heroChevronRight, heroMagnifyingGlass } from '@ng-icons/heroicons/outline';
import { DatePipe } from '@core/i18n/date.pipe';
import { MoneyPipe } from '@core/money/money.pipe';
import { Button } from '@shared/ui/button/button';
import { Spinner } from '@shared/ui/spinner/spinner';
import { IBusDeparture } from '@shared/models/bus.model';
import { BusService } from '../bus.service';
import { BUS_LIMITS, dakarToday, parseBusSearch, shiftDay, toBusQueryParams } from '../bus-search-params';
import { BusSearchForm } from '../search-form/bus-search-form';

/**
 * Départs de car d'un jour : un par horaire, avec l'heure (de Dakar), les gares, le prix et les places restantes.
 * Jour précédent ou suivant d'un clic ; un départ choisi ouvre la réservation.
 */
@Component({
  selector: 'bip-bus-search',
  imports: [TranslocoDirective, NgIcon, Button, Spinner, DatePipe, MoneyPipe, BusSearchForm],
  providers: [provideIcons({ heroArrowRight, heroChevronLeft, heroChevronRight, heroMagnifyingGlass })],
  templateUrl: './bus-search.html',
})
export class BusSearch {
  readonly from = input<string>();
  readonly to = input<string>();
  readonly date = input<string>();
  readonly passengers = input<string>();

  protected readonly search = computed(() => parseBusSearch({ from: this.from(), to: this.to(), date: this.date(), passengers: this.passengers() }));
  protected readonly editing = signal(false);
  /** Heure de Dakar : GMT, quel que soit le fuseau du navigateur. */
  protected readonly time: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' };
  protected readonly day: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' };

  private readonly api = inject(BusService);
  private readonly router = inject(Router);
  private readonly places = toSignal(this.api.places().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly route = computed(() => {
    const search = this.search();
    const name = (id: string) => this.places().find((place) => place.id === id)?.name ?? '';
    return search ? { from: name(search.from), to: name(search.to) } : null;
  });

  private readonly results = rxResource({
    params: () => this.search() ?? undefined,
    stream: ({ params }) => this.api.departures(params),
  });
  protected readonly loading = this.results.isLoading;
  protected readonly failed = computed(() => this.results.status() === 'error');
  protected readonly departures = computed((): IBusDeparture[] => (this.results.hasValue() ? this.results.value() : []));
  protected readonly canGoBack = computed(() => !!this.search() && this.search()!.date > dakarToday());
  protected readonly canGoForward = computed(() => !!this.search() && this.search()!.date < shiftDay(dakarToday(), BUS_LIMITS.daysAhead));

  protected changeDay(days: number): void {
    const search = this.search();
    if (search) {
      void this.router.navigate([], { queryParams: toBusQueryParams({ ...search, date: shiftDay(search.date, days) }) });
    }
  }

  protected choose(departure: IBusDeparture): void {
    const search = this.search();
    if (!search || !departure.available) {
      return;
    }
    void this.router.navigate(['/bus', 'book'], {
      queryParams: { route: departure.route_code, departs: departure.departs_at, passengers: search.passengers, from: search.from, to: search.to },
    });
  }

  protected retry(): void {
    this.results.reload();
  }
}
