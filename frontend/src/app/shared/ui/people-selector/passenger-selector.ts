// noinspection ES6PreferShortImport

import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { Dropdown } from '../dropdown/dropdown';
import { Button } from '../button/button';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';

import { isValidPassengerCount, maximumAdults, maximumChildren, maximumInfants, minimumAdults, IPassengerCount } from './passenger.helper';
import { FormValueControl, ValidationError } from '@angular/forms/signals';

type PassengerType = keyof IPassengerCount;

interface PassengerRow {
  readonly key: PassengerType;
  readonly label: string;
  readonly hint: string;
  readonly value: number;
  readonly min: number;
  readonly max: number;
}

@Component({
  selector: 'bip-passenger-selector',
  imports: [Dropdown, Button, TranslocoDirective],
  host: { class: 'block' },
  templateUrl: './passenger-selector.html',
})
export class PassengerSelector implements FormValueControl<IPassengerCount> {
  readonly value = model<IPassengerCount>({ adults: 1, children: 0, infants: 0 });
  readonly errors = input<readonly ValidationError.WithOptionalFieldTree[]>([]);
  readonly invalid = input(false);
  readonly touched = input(false);
  readonly open = signal(false);
  readonly touch = output<void>();

  protected readonly hasPassengerCompositionError = computed(() => !isValidPassengerCount(this.value()));

  protected readonly rows = computed<readonly PassengerRow[]>(() => {
    const { adults, children, infants } = this.value();
    return [
      {
        key: 'adults',
        label: 'search.passenger.adult.label',
        hint: 'search.passenger.adult.hint',
        value: adults,
        min: minimumAdults(children, infants),
        max: maximumAdults(children, infants),
      },
      {
        key: 'children',
        label: 'search.passenger.children.label',
        hint: 'search.passenger.children.hint',
        value: children,
        min: 0,
        max: maximumChildren(adults, infants),
      },
      {
        key: 'infants',
        label: 'search.passenger.infant.label',
        hint: 'search.passenger.infant.hint',
        value: infants,
        min: 0,
        max: maximumInfants(adults, children),
      },
    ];
  });

  private readonly transloco = inject(TranslocoService);
  protected readonly summary = computed(() => {
    const lang = this.transloco.activeLang();
    const { adults, children, infants } = this.value();
    return [
      adults > 0 ? this.transloco.translate('search.passenger.adult.detailed', { count: adults }, lang) : null,
      children > 0 ? this.transloco.translate('search.passenger.children.detailed', { count: children }, lang) : null,
      infants > 0 ? this.transloco.translate('search.passenger.infant.detailed', { count: infants }, lang) : null,
    ]
      .filter((summary): summary is string => summary !== null)
      .join(', ');
  });

  protected step(key: PassengerType, delta: -1 | 1): void {
    this.touch.emit();
    this.value.update((current) => {
      const next: IPassengerCount = { ...current, [key]: current[key] + delta };
      return isValidPassengerCount(next) ? next : current;
    });
  }
}
