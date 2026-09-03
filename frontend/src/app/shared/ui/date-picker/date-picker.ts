import {
  booleanAttribute,
  Component,
  computed,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { Dropdown } from '../dropdown/dropdown';
import { Button } from '../button/button';
import {
  addMonths,
  DayCell,
  formatDisplay,
  fromIso,
  IsoDate,
  isBetween,
  isSameDay,
  monthGrid,
  monthLabel,
  todayIso,
  weekdayLabels,
} from './date-utils';

export interface DateRangeValue {
  readonly start: IsoDate | null;
  readonly end: IsoDate | null;
}

/**
 * Calendrier maison — aucune dépendance externe. Deux modes : date simple
 * (aller simple) et plage (aller-retour). Les dates indisponibles sont fournies
 * par l'appelant, jamais devinées.
 */
@Component({
  selector: 'bip-date-picker',
  imports: [Dropdown, Button],
  host: { class: 'block' },
  template: `
    <bip-dropdown
      [(open)]="open"
      [label]="label()"
      [value]="displayValue()"
      [placeholder]="placeholder()"
      [disabled]="disabled()"
      [error]="!!error()"
      panelWidth="auto">
      <div class="p-3">
        <div class="flex items-center justify-between gap-2 pb-2">
          <button
            bipButton
            variant="ghost"
            size="sm"
            type="button"
            [disabled]="!canGoBack()"
            (click)="shift(-1)">
            <span class="sr-only">Mois précédent</span>
            <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M12.7 4.3a1 1 0 0 1 0 1.4L8.4 10l4.3 4.3a1 1 0 1 1-1.4 1.4l-5-5a1 1 0 0 1 0-1.4l5-5a1 1 0 0 1 1.4 0Z" />
            </svg>
          </button>
          <div class="flex flex-1 justify-around gap-6">
            @for (month of months(); track month.key) {
              <p class="text-small text-ink font-semibold capitalize">{{ month.label }}</p>
            }
          </div>
          <button bipButton variant="ghost" size="sm" type="button" (click)="shift(1)">
            <span class="sr-only">Mois suivant</span>
            <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M7.3 15.7a1 1 0 0 1 0-1.4l4.3-4.3-4.3-4.3a1 1 0 0 1 1.4-1.4l5 5a1 1 0 0 1 0 1.4l-5 5a1 1 0 0 1-1.4 0Z" />
            </svg>
          </button>
        </div>

        <div class="flex gap-6">
          @for (month of months(); track month.key) {
            <table class="border-separate border-spacing-0.5" role="grid">
              <thead>
                <tr>
                  @for (day of weekdays(); track day) {
                    <th scope="col" class="text-label text-muted h-8 w-9 font-medium capitalize">
                      {{ day }}
                    </th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (week of month.weeks; track $index) {
                  <tr>
                    @for (cell of week; track cell.iso) {
                      <td class="p-0">
                        @if (cell.inMonth) {
                          <button
                            type="button"
                            [class]="dayClass(cell)"
                            [disabled]="isDisabled(cell.iso)"
                            [attr.aria-pressed]="isEdge(cell.iso)"
                            (click)="pick(cell.iso)"
                            (mouseenter)="hovered.set(cell.iso)"
                            (mouseleave)="hovered.set(null)">
                            {{ cell.dayNumber }}
                          </button>
                        } @else {
                          <span class="block h-9 w-9"></span>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>

        <div class="border-border-subtle mt-2 flex items-center justify-between gap-2 border-t pt-2">
          <p class="text-label text-muted">{{ helperText() }}</p>
          <button bipButton variant="ghost" size="sm" type="button" (click)="clear()">Effacer</button>
        </div>
      </div>
    </bip-dropdown>

    @if (error()) {
      <p class="text-label text-state-error mt-1.5">{{ error() }}</p>
    }
  `,
})
export class DatePicker {
  readonly label = input<string>();
  readonly mode = input<'single' | 'range'>('single');
  readonly placeholder = input('Choisir une date');
  readonly locale = input('fr-FR');
  readonly min = input<IsoDate | null>(todayIso());
  readonly max = input<IsoDate | null>(null);
  readonly unavailable = input<readonly IsoDate[]>([]);
  readonly error = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Deux mois côte à côte à partir de `md` : réglé par l'appelant. */
  readonly visibleMonths = input(1);

  readonly value = model<DateRangeValue>({ start: null, end: null });
  readonly changed = output<DateRangeValue>();

  protected readonly open = model(false);
  protected readonly hovered = signal<IsoDate | null>(null);
  private readonly anchor = signal(new Date());

  protected readonly weekdays = computed(() => weekdayLabels(this.locale()));

  protected readonly months = computed(() => {
    const count = Math.max(1, this.visibleMonths());
    return Array.from({ length: count }, (_, i) => {
      const date = addMonths(this.anchor(), i);
      const cells = monthGrid(date);
      const weeks: DayCell[][] = [];
      for (let w = 0; w < 6; w++) {
        weeks.push(cells.slice(w * 7, w * 7 + 7));
      }
      return { key: `${date.getFullYear()}-${date.getMonth()}`, label: monthLabel(date, this.locale()), weeks };
    });
  });

  protected readonly displayValue = computed(() => {
    const { start, end } = this.value();
    if (this.mode() === 'single') {
      return formatDisplay(start, this.locale());
    }
    if (!start) {
      return '';
    }
    return end
      ? `${formatDisplay(start, this.locale())} → ${formatDisplay(end, this.locale())}`
      : `${formatDisplay(start, this.locale())} → …`;
  });

  protected readonly helperText = computed(() => {
    if (this.mode() === 'single') {
      return 'Sélectionnez une date';
    }
    return this.value().start && !this.value().end
      ? 'Sélectionnez la date de retour'
      : 'Sélectionnez la date de départ';
  });

  protected readonly canGoBack = computed(() => {
    const min = this.min();
    if (!min) {
      return true;
    }
    const first = this.anchor();
    return new Date(first.getFullYear(), first.getMonth(), 1) > fromIso(min);
  });

  protected shift(delta: number): void {
    this.anchor.update((a) => addMonths(a, delta));
  }

  protected isDisabled(iso: IsoDate): boolean {
    const min = this.min();
    const max = this.max();
    if (min && iso < min) {
      return true;
    }
    if (max && iso > max) {
      return true;
    }
    return this.unavailable().includes(iso);
  }

  protected isEdge(iso: IsoDate): boolean {
    const { start, end } = this.value();
    return isSameDay(iso, start) || isSameDay(iso, end);
  }

  protected dayClass(cell: DayCell): string {
    const { start, end } = this.value();
    const preview = this.mode() === 'range' && start && !end ? this.hovered() : null;
    const inRange =
      isBetween(cell.iso, start, end) || (preview ? isBetween(cell.iso, start, preview) : false);

    const base = [
      'h-9 w-9 rounded-button text-small transition-colors bip-focusable',
      'disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through',
    ];
    if (this.isEdge(cell.iso)) {
      base.push('bg-brand-primary text-onbrand font-semibold');
    } else if (inRange) {
      base.push('bg-brand-soft text-brand-dark');
    } else {
      base.push('text-ink hover:bg-brand-soft');
    }
    if (cell.iso === todayIso() && !this.isEdge(cell.iso)) {
      base.push('ring-brand-primary/40 ring-1');
    }
    return base.join(' ');
  }

  protected pick(iso: IsoDate): void {
    if (this.isDisabled(iso)) {
      return;
    }
    if (this.mode() === 'single') {
      this.commit({ start: iso, end: null });
      this.open.set(false);
      return;
    }
    const { start, end } = this.value();
    if (!start || end || iso < start) {
      this.commit({ start: iso, end: null });
    } else {
      this.commit({ start, end: iso });
      this.open.set(false);
    }
  }

  protected clear(): void {
    this.commit({ start: null, end: null });
  }

  private commit(next: DateRangeValue): void {
    this.value.set(next);
    this.changed.emit(next);
  }
}
