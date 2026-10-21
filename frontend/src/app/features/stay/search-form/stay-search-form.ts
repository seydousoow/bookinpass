import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { form, FormField, FormRoot, required, validate } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Button } from '@shared/ui/button/button';
import { DatePicker } from '@shared/ui/date-picker/date-picker';
import { fromIso, isAfterDay, isBeforeDay, todayIso, toIso } from '@shared/ui/date-picker/date-utils';
import { IStaySearch } from '@shared/models/stay.model';
import { StaySearchStore } from '@core/search/stay-search.store';
import { isValidStayGuests, maxCheckIn, nightsBetween, STAY_LIMITS, toStayQueryParams } from '../search/stay-search-params';
import { GuestSelector } from './guest-selector/guest-selector';
import { DestinationField } from './destination-field/destination-field';
import { isDestinationId } from './destination-field/stay-destination.service';

/**
 * Recherche d'hébergement : ville, dates d'arrivée et de départ, chambres et voyageurs.
 * La soumission ouvre `/stays/search` avec les critères dans l'URL.
 */
@Component({
  selector: 'bip-stay-search-form',
  imports: [TranslocoDirective, DatePicker, DestinationField, GuestSelector, Button, FormField, FormRoot],
  templateUrl: './stay-search-form.html',
})
export class StaySearchForm {
  /** Critères à afficher (page de résultats) ; sans eux, la dernière recherche mémorisée préremplit le formulaire. */
  readonly initial = input<IStaySearch | null>(null);

  protected readonly minDate = todayIso();
  private readonly search = signal<IStaySearch>({
    location: '',
    dates: { start: null, end: null },
    guests: { rooms: 1, adults: 2, childAges: [] },
  });
  /** Départ : 99 nuits au plus après l'arrivée choisie. */
  protected readonly maxDate = computed(() => {
    const start = this.search().dates.start;
    const date = fromIso(start ?? maxCheckIn());
    date.setDate(date.getDate() + STAY_LIMITS.maxNights);
    return toIso(date);
  });
  private readonly router = inject(Router);
  protected readonly searchForm = form<IStaySearch>(
    this.search,
    (path) => {
      required(path.location, { message: 'common.required-field' });
      validate(path.dates, ({ value }) => {
        const { start, end } = value();
        if (!start) {
          return { kind: 'requiredStart', message: 'stay.search.errors.check-in-required' };
        }
        if (isBeforeDay(start, todayIso()) || isAfterDay(start, maxCheckIn())) {
          return { kind: 'checkInRange', message: 'stay.search.errors.check-in-range' };
        }
        if (!end) {
          return { kind: 'requiredEnd', message: 'stay.search.errors.check-out-required' };
        }
        const nights = nightsBetween(start, end);
        if (nights < 1 || nights > STAY_LIMITS.maxNights) {
          return { kind: 'stayLength', message: 'stay.search.errors.stay-length' };
        }
        return null;
      });
      validate(path.guests, ({ value }) => (isValidStayGuests(value()) ? null : { kind: 'guests', message: 'stay.guests.invalid' }));
    },
    {
      submission: {
        action: (f) => {
          void this.router.navigate(['/stays', 'search'], { queryParams: toStayQueryParams(f().value()) });
          return Promise.resolve(null);
        },
        ignoreValidators: 'none',
        onInvalid: (field) => field().errorSummary()[0]?.fieldTree().focusBoundControl(),
      },
    },
  );
  private readonly store = inject(StaySearchStore);
  private readonly prefilled = signal(false);

  constructor() {
    // Une seule fois : critères de la page, sinon dernière recherche — sans écraser une saisie en cours ensuite.
    effect(() => {
      const source = this.initial() ?? this.store.lastSearch();
      if (!source || untracked(this.prefilled)) {
        return;
      }
      this.prefilled.set(true);
      // Dates passées d'une ancienne recherche : on garde la ville et les voyageurs seulement.
      const stale = !source.dates.start || isBeforeDay(source.dates.start, todayIso());
      // Ancienne recherche par code IATA : la ville est à choisir de nouveau.
      const location = isDestinationId(source.location) ? source.location : '';
      this.search.set({ ...source, location, dates: stale ? { start: null, end: null } : source.dates });
    });
  }
}
