import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';

/**
 * Date à la locale active, options `Intl` telles quelles : `{{ iso | bipDate: { day: 'numeric', month: 'short' } }}`.
 * `translocoDate` ajoute un `dateStyle` par défaut, qu'`Intl` refuse avec `day`, `month` ou `weekday`.
 * Un jour civil (`2026-11-20`) se lit en UTC : passer `timeZone: 'UTC'` pour qu'il ne glisse pas d'un jour.
 *
 * Impur pour suivre le changement de langue ; mémoïsé sur ses entrées.
 */
@Pipe({ name: 'bipDate', pure: false })
export class DatePipe implements PipeTransform {
  private readonly locale = inject(TranslocoLocaleService);
  private lastKey = '';
  private lastValue = '';

  transform(value: string | null | undefined, options: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }): string {
    if (!value || Number.isNaN(Date.parse(value))) {
      return '';
    }
    const locale = this.locale.getLocale();
    const key = `${value}|${locale}|${JSON.stringify(options)}`;
    if (key !== this.lastKey) {
      this.lastKey = key;
      this.lastValue = new Intl.DateTimeFormat(locale, options).format(new Date(value));
    }
    return this.lastValue;
  }
}
