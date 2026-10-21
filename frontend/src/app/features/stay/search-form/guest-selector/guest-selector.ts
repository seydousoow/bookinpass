import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormValueControl, ValidationError } from '@angular/forms/signals';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { Button } from '@shared/ui/button/button';
import { Dropdown } from '@shared/ui/dropdown/dropdown';
import { IStayGuests } from '@shared/models/stay.model';
import { isValidStayGuests, STAY_LIMITS } from '../../search/stay-search-params';

type TCounter = 'rooms' | 'adults' | 'children';

interface ICounterRow {
  readonly key: TCounter;
  readonly value: number;
  readonly min: number;
  readonly max: number;
}

/** Âge proposé pour un enfant ajouté : modifiable aussitôt dans sa liste. */
const DEFAULT_CHILD_AGE = 8;

/** Chambres, adultes et enfants (avec l'âge de chacun, exigé par les hôtels). */
@Component({
  selector: 'bip-guest-selector',
  imports: [Dropdown, Button, TranslocoDirective],
  host: { class: 'block' },
  templateUrl: './guest-selector.html',
})
export class GuestSelector implements FormValueControl<IStayGuests> {
  readonly value = model<IStayGuests>({ rooms: 1, adults: 2, childAges: [] });
  readonly errors = input<readonly ValidationError.WithOptionalFieldTree[]>([]);
  readonly invalid = input(false);
  readonly touched = input(false);
  readonly touch = output<void>();
  protected readonly open = signal(false);
  protected readonly ages = Array.from({ length: STAY_LIMITS.maxChildAge + 1 }, (_, age) => age);

  protected readonly rows = computed((): readonly ICounterRow[] => {
    const { rooms, adults, childAges } = this.value();
    return [
      { key: 'rooms', value: rooms, min: 1, max: Math.min(STAY_LIMITS.maxRooms, adults) },
      { key: 'adults', value: adults, min: rooms, max: STAY_LIMITS.maxAdults },
      { key: 'children', value: childAges.length, min: 0, max: STAY_LIMITS.maxChildren },
    ];
  });

  private readonly transloco = inject(TranslocoService);
  protected readonly summary = computed(() => {
    const lang = this.transloco.activeLang();
    const { rooms, adults, childAges } = this.value();
    return [
      this.transloco.translate('stay.guests.adults-count', { count: adults }, lang),
      childAges.length ? this.transloco.translate('stay.guests.children-count', { count: childAges.length }, lang) : null,
      this.transloco.translate('stay.guests.rooms-count', { count: rooms }, lang),
    ]
      .filter((part): part is string => part !== null)
      .join(', ');
  });

  protected step(key: TCounter, delta: -1 | 1): void {
    this.touch.emit();
    this.value.update((current) => {
      const next: IStayGuests =
        key === 'children'
          ? { ...current, childAges: delta > 0 ? [...current.childAges, DEFAULT_CHILD_AGE] : current.childAges.slice(0, -1) }
          : { ...current, [key]: current[key] + delta };
      return isValidStayGuests(next) ? next : current;
    });
  }

  protected setAge(index: number, event: Event): void {
    const age = Number((event.target as HTMLSelectElement).value);
    this.value.update((current) => ({ ...current, childAges: current.childAges.map((a, i) => (i === index ? age : a)) }));
  }
}
