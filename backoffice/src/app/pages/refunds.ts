import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { dakarDateTime } from '@core/dakar';

/**
 * Remboursements (admin, manager) : paiements Wave / Orange Money reçus après la revente des places. Le mobile money ne
 * s'annule pas : le client est remboursé à la main, puis le remboursement est noté ici (opérateur, référence).
 */
@Component({
  selector: 'bo-refunds',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <div class="flex flex-wrap items-end gap-3">
        <h1 class="mr-auto text-xl font-bold">{{ t('refunds.title') }}</h1>
        <label class="flex min-h-11 items-center gap-2 text-sm">
          <input [ngModel]="all()" (ngModelChange)="all.set($event)" type="checkbox"/> {{ t('refunds.show-done') }}
        </label>
      </div>
      <p class="text-muted text-xs">{{ t('refunds.hint') }}</p>
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }
      @if (list.isLoading() && !list.hasValue()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      } @else if (!list.value()?.length) {
        <p class="bo-card text-muted">{{ t('refunds.none') }}</p>
      }
      <ul class="grid gap-2">
        @for (r of list.value() ?? []; track r.id) {
          <li [class.opacity-70]="r.status === 'refunded'" class="bo-card space-y-2 text-sm">
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div>
                <p><span class="font-mono text-base font-bold">{{ r.reference }}</span> · {{ r.origin }} → {{ r.destination }} · {{ dateTime(r.departs_at) }}</p>
                <p>{{ r.passengers }}</p>
                <p class="text-muted">{{ r.email }} · <a [href]="'tel:' + r.phone" class="underline">{{ r.phone }}</a></p>
              </div>
              <div class="text-right">
                <p class="text-lg font-bold">{{ r.amount }} {{ r.currency }}</p>
                <p class="text-muted text-xs">{{ t('refunds.paid-at', {time: dateTime(r.paid_at)}) }}</p>
                @if (r.receipt_url) {
                  <a [href]="r.receipt_url" class="text-brand-primary text-xs underline" rel="noopener" target="_blank">{{ t('refunds.receipt') }}</a>
                }
                <p class="text-muted font-mono text-xs">{{ r.provider }} · {{ r.invoice_token }}</p>
              </div>
            </div>
            @if (r.status === 'refunded') {
              <p class="text-state-success">✓ {{ t('refunds.done', {time: dateTime(r.refunded_at), name: r.refunded_by}) }} — {{ r.refund_note }}</p>
            } @else {
              <form (ngSubmit)="markRefunded(r.id)" class="border-border-subtle flex flex-wrap gap-2 border-t pt-2">
                <input [(ngModel)]="notes[r.id]" [name]="'note-' + r.id" [placeholder]="t('refunds.note')" class="bo-input min-w-64 flex-1" maxlength="500"/>
                <button [disabled]="busy() || !notes[r.id]?.trim()" class="bo-btn-primary" type="submit">{{ t('refunds.mark') }}</button>
              </form>
            }
          </li>
        }
      </ul>
    </section>
  `,
})
export class RefundsPage {
  private readonly api = inject(StaffApi);
  protected readonly all = signal(false);
  protected readonly list = rxResource({
    params: () => this.all(),
    stream: ({ params }) => this.api.refunds(params),
  });
  protected readonly notes: Record<number, string> = {};
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dateTime = dakarDateTime;

  protected markRefunded(id: number): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .markRefunded(id, this.notes[id].trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.list.reload(),
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
