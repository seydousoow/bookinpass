import { Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { TStayStep } from './stay-draft';

/** Étapes de la réservation ; sur mobile, seule l'étape en cours reste visible. */
@Component({
  selector: 'bip-stay-stepper',
  imports: [TranslocoDirective],
  template: `
    <nav *transloco="let t; prefix: 'stay.steps'" [attr.aria-label]="t('label')" class="bg-surface-muted -mx-2 mb-4 px-4 py-3">
      <ol class="flex items-center gap-3 overflow-x-auto">
        @for (step of steps(); track step; let i = $index, last = $last) {
          <li [attr.aria-current]="i === active() ? 'step' : null"
              [class.max-md:hidden]="i !== active()"
              [class.text-muted]="i > active()"
              class="text-ink flex shrink-0 items-center gap-2 text-sm font-semibold">
            <span
              [class]="i === active() ? 'bg-brand-primary border-brand-primary text-onbrand' : i < active() ? 'border-brand-primary text-brand-primary' : 'border-ink'"
              class="flex size-6 items-center justify-center rounded-full border-2 text-xs">{{ i + 1 }}</span>
            {{ t(step) }}
          </li>
          @if (!last) {
            <li aria-hidden="true" class="bg-border-strong h-px w-10 shrink-0 max-md:hidden"></li>
          }
        }
      </ol>
    </nav>
  `,
})
export class StayStepper {
  readonly steps = input.required<readonly TStayStep[]>();
  readonly active = input.required<number>();
}
