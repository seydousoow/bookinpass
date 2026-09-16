// noinspection ES6PreferShortImport

import { booleanAttribute, Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCalendarDateRangeSolid, heroCalendarSolid, heroChevronLeftSolid, heroChevronRightSolid } from '@ng-icons/heroicons/solid';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { Button } from '../button/button';
import { Dropdown } from '../dropdown/dropdown';
import { addMonths, DayCell, formatDisplay, fromIso, isBetween, IsoDate, isSameDay, monthGrid, monthLabel, todayIso } from './date-utils';
import { DateRangeValue } from '@shared/models';

export type DatePickerMode = 'single' | 'range';

@Component({
  selector: 'bip-date-picker',
  imports: [Button, NgIcon, TranslocoDirective, TranslocoPipe, Dropdown],
  host: { class: 'block' },
  providers: [provideIcons({ heroChevronLeftSolid, heroChevronRightSolid })],
  templateUrl: './date-picker.html',
})
export class DatePicker implements FormValueControl<DateRangeValue> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly mode = input<DatePickerMode>('single');

  readonly prefixIcon = computed(() => (this.mode() === 'range' ? heroCalendarDateRangeSolid : heroCalendarSolid));

  readonly placeholder = input<string>('picker.placeholder');
  readonly unavailable = input<readonly IsoDate[]>([]);
  readonly visibleMonths = input(1);

  readonly minDate = input<IsoDate | null>(null);
  readonly maxDate = input<IsoDate | null>(null);

  readonly name = input('');
  readonly touched = input(false);
  readonly invalid = input(false);
  readonly dirty = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly touch = output<void>();
  readonly value = model<DateRangeValue>({ start: null, end: null });

  protected readonly opened = signal(false);
  protected readonly hovered = signal<IsoDate | null>(null);
  private readonly transloco = inject(TranslocoService);
  protected readonly helperText = computed(() => {
    if (this.mode() === 'single') {
      return this.transloco.translate('picker.select.one-way');
    }
    const { start, end } = this.value();
    if (!start) {
      return this.transloco.translate('picker.select.outbound');
    }
    return this.transloco.translate(!end ? 'picker.select.inbound' : 'picker.select.outbound');
  });
  protected readonly weekdays = computed(() => {
    const locale = this.transloco.activeLang();
    const formatter = new Intl.DateTimeFormat(locale, { weekday: 'short' });
    return Array.from({ length: 7 }, (_, index) => formatter.format(new Date(2024, 0, 1 + index)).replace('.', ''));
  });
  protected readonly displayValue = computed(() => {
    const locale = this.transloco.activeLang();
    const { start, end } = this.value();
    if (!start) {
      return '';
    }
    if (this.mode() === 'single') {
      return formatDisplay(start, locale);
    }
    return !end ? `${formatDisplay(start, locale)} → …` : `${formatDisplay(start, locale)} → ${formatDisplay(end, locale)}`;
  });
  /**
   * First month displayed by the calendar.
   *
   * The actual value is always normalized to the first day of the month.
   */
  private readonly anchor = signal(new Date());
  /**
   * The month that should actually be displayed.
   *
   * It is clamped between:
   *
   *   minDate month
   *
   * and
   *
   *   maxDate month - visibleMonths + 1
   *
   * This prevents rendering months completely outside the allowed range.
   */
  protected readonly displayAnchor = computed(() => {
    const anchor = this.monthStart(this.anchor());
    const visible = Math.max(1, this.visibleMonths());

    const min = this.minDate();
    const max = this.maxDate();
    let result = anchor;

    if (min) {
      const minMonth = this.monthStart(fromIso(min));
      if (result < minMonth) {
        result = minMonth;
      }
    }

    // Don't display months after maxDate.
    //
    // Example:
    // visibleMonths = 2
    // maxDate = March
    //
    // Last valid anchor = February
    // => February | March
    if (max) {
      const maxMonth = this.monthStart(fromIso(max));
      const lastValidAnchor = addMonths(maxMonth, -(visible - 1));

      if (result > lastValidAnchor) {
        result = lastValidAnchor;
      }
    }

    return result;
  });
  /**
   * Previous button is disabled when the first visible month
   * is already the minDate month.
   */
  protected readonly canGoBack = computed(() => {
    const min = this.minDate();
    if (!min) {
      return true;
    }

    const firstMonth = this.monthStart(this.displayAnchor());
    const minMonth = this.monthStart(fromIso(min));
    return firstMonth > minMonth;
  });
  /**
   * Next button is disabled when the last visible month
   * is already the maxDate month.
   */
  protected readonly canGoForward = computed(() => {
    const max = this.maxDate();
    if (!max) {
      return true;
    }

    const visible = Math.max(1, this.visibleMonths());
    const lastVisibleMonth = addMonths(this.displayAnchor(), visible - 1);
    const maxMonth = this.monthStart(fromIso(max));
    return lastVisibleMonth < maxMonth;
  });
  protected readonly months = computed(() => {
    const locale = this.transloco.activeLang();
    const count = Math.max(1, this.visibleMonths());
    const anchor = this.displayAnchor();

    return Array.from({ length: count }, (_, index) => {
      const date = addMonths(anchor, index);
      const cells = monthGrid(date);
      const weeks: DayCell[][] = [];
      for (let week = 0; week < 6; week++) {
        weeks.push(cells.slice(week * 7, week * 7 + 7));
      }
      return { key: `${date.getFullYear()}-${date.getMonth()}`, label: monthLabel(date, locale), weeks };
    });
  });

  clear(): void {
    this.value.set({ start: null, end: null });
    this.hovered.set(null);
    this.reset();
  }

  reset(): void {
    // Intentionally empty.
  }

  protected shift(delta: number): void {
    const current = this.displayAnchor();
    this.anchor.set(addMonths(current, delta));
  }

  protected isDisabled(iso: IsoDate): boolean {
    const min = this.minDate();
    const max = this.maxDate();
    return (min && iso < min) || (max && iso > max) || this.unavailable().includes(iso);
  }

  protected isEdge(iso: IsoDate): boolean {
    const { start, end } = this.value();
    return isSameDay(iso, start) || isSameDay(iso, end);
  }

  protected dayClass(cell: DayCell): string {
    const { start, end } = this.value();
    const preview = this.mode() === 'range' && !!start && !end ? this.hovered() : null;
    const inRange = isBetween(cell.iso, start, end) || (!!preview && isBetween(cell.iso, start, preview));
    const base = [
      'min-h-9 min-w-9 rounded-button text-sm',
      'transition-colors bip-focusable',
      'disabled:cursor-not-allowed',
      'disabled:opacity-30',
      'disabled:line-through',
    ];

    if (this.isEdge(cell.iso)) {
      base.push('bg-brand-primary', 'text-onbrand', 'font-semibold');
    } else if (inRange) {
      base.push('bg-brand-soft', 'text-brand-dark');
    } else {
      base.push('text-ink', 'hover:bg-brand-soft');
    }

    if (cell.iso === todayIso() && !this.isEdge(cell.iso)) {
      base.push('ring-1 ring-brand-primary/40');
    }

    return base.join(' ');
  }

  protected pick(iso: IsoDate): void {
    if (this.isDisabled(iso)) {
      return;
    }
    this.hovered.set(null);

    /**
     * Single date mode:
     * selected date is always the start.
     */
    if (this.mode() === 'single') {
      this.value.set({ start: iso, end: null });
      this.opened.set(false);
      return;
    }

    const { start, end } = this.value();

    /**
     * Range mode:
     *
     * - no start => create start
     * - range already complete => create new start
     * - clicked before start => create new start
     * - otherwise => create end
     */
    if (!start || !!end || iso < start) {
      this.value.set({ start: iso, end: null });
      return;
    }
    this.value.set({ start, end: iso });
    this.opened.set(false);
  }

  private monthStart(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }
}
