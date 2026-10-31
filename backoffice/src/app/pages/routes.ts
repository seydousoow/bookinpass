import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, Observable, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Route } from '@core/models';

interface RouteDraft {
  price: number;
  capacity: number;
  active: boolean;
}

interface ScheduleDraft {
  days: boolean[];
  departs_at: string;
  last_departure: string;
}

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const;

/**
 * Réseau (admin, manager) : prix par passager (frais Book In Pass en plus), places d'un car, mise en vente et horaires
 * de chaque ligne ; création d'une ligne entre deux gares. Les heures sont celles de Dakar.
 */
@Component({
  selector: 'bo-routes',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <h1 class="text-xl font-bold">{{ t('routes.title') }}</h1>
      <p class="text-muted text-xs">{{ t('routes.hint') }}</p>
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }

      <form (ngSubmit)="create()" class="bo-card grid gap-3 sm:grid-cols-2 lg:grid-cols-6 lg:items-end">
        <h2 class="font-bold sm:col-span-2 lg:col-span-6">{{ t('routes.new') }}</h2>
        <label class="lg:col-span-2"><span class="bo-label">{{ t('routes.origin') }}</span>
          <select [(ngModel)]="draft.origin" class="bo-input" name="origin" required>
            @for (s of stations(); track s.id) {
              <option [ngValue]="s.id">{{ s.name }} ({{ s.place }})</option>
            }
          </select>
        </label>
        <label class="lg:col-span-2"><span class="bo-label">{{ t('routes.destination') }}</span>
          <select [(ngModel)]="draft.destination" class="bo-input" name="destination" required>
            @for (s of stations(); track s.id) {
              <option [ngValue]="s.id">{{ s.name }} ({{ s.place }})</option>
            }
          </select>
        </label>
        <label><span class="bo-label">{{ t('routes.network') }}</span>
          <select [(ngModel)]="draft.network" class="bo-input" name="network">
            @for (n of networks; track n) {
              <option [value]="n">{{ t('routes.networks.' + n) }}</option>
            }
          </select>
        </label>
        <label><span class="bo-label">{{ t('routes.price') }}</span><input [(ngModel)]="draft.price" class="bo-input" min="1" name="price" required step="1" type="number"/></label>
        <label><span class="bo-label">{{ t('routes.capacity') }}</span><input [(ngModel)]="draft.capacity" class="bo-input" min="1" name="capacity" required type="number"/></label>
        <button [disabled]="busy() || !draft.origin || !draft.destination" class="bo-btn-primary" type="submit">{{ t('routes.create') }}</button>
      </form>

      @if (list.isLoading() && !list.hasValue()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      }
      @for (r of list.value() ?? []; track r.id) {
        <article [class.opacity-60]="!r.active" class="bo-card space-y-3">
          <div class="flex flex-wrap items-end gap-3">
            <div class="mr-auto">
              <h2 class="font-bold">{{ r.origin.place }} → {{ r.destination.place }}</h2>
              <p class="text-muted text-xs">{{ r.origin.name }} → {{ r.destination.name }} · {{ t('routes.networks.' + r.network) }} · {{ t('routes.fee', {fee: r.fee}) }}</p>
            </div>
            <label><span class="bo-label">{{ t('routes.price') }}</span><input [(ngModel)]="edit(r).price" class="bo-input w-28" min="1" step="1" type="number"/></label>
            <label><span class="bo-label">{{ t('routes.capacity') }}</span><input [(ngModel)]="edit(r).capacity" class="bo-input w-20" min="1" type="number"/></label>
            <label class="flex min-h-11 items-center gap-2 text-sm"><input [(ngModel)]="edit(r).active" type="checkbox"/> {{ t('routes.on-sale') }}</label>
            <button (click)="save(r)" [disabled]="busy()" class="bo-btn-outline" type="button">{{ t('common.save') }}</button>
          </div>
          <div class="flex flex-wrap gap-2 text-sm">
            @for (s of r.schedules; track s.id) {
              <span class="bg-surface-muted border-border-subtle inline-flex items-center gap-1 rounded-pill border py-1 pr-1 pl-3">
                {{ t('days.' + s.day_of_week) }} {{ s.departs_at }}{{ s.last_departure ? '–' + s.last_departure : '' }}
                <button (click)="run(api.deleteSchedule(s.id))" [attr.aria-label]="t('routes.remove-schedule')" [disabled]="busy()"
                        class="bo-focusable hover:bg-state-error-soft rounded-pill size-7" type="button">×</button>
              </span>
            } @empty {
              <span class="text-muted">{{ t('routes.no-schedule') }}</span>
            }
          </div>
          <form (ngSubmit)="addSchedule(r)" class="border-border-subtle flex flex-wrap items-end gap-3 border-t pt-3">
            <fieldset class="flex flex-wrap gap-2">
              <legend class="bo-label">{{ t('routes.days') }}</legend>
              @for (day of days; track day) {
                <label class="flex items-center gap-1 text-sm"><input [(ngModel)]="schedule(r).days[day - 1]" [name]="'d' + day" type="checkbox"/> {{ t('days.' + day) }}</label>
              }
            </fieldset>
            <label><span class="bo-label">{{ t('routes.departs-at') }}</span><input [(ngModel)]="schedule(r).departs_at" class="bo-input" name="at" required type="time"/></label>
            <label><span class="bo-label">{{ t('routes.last-departure') }}</span><input [(ngModel)]="schedule(r).last_departure" class="bo-input" name="last" type="time"/></label>
            <button [disabled]="busy() || !schedule(r).departs_at" class="bo-btn-outline" type="submit">{{ t('routes.add-schedule') }}</button>
          </form>
        </article>
      }
    </section>
  `,
})
export class RoutesPage {
  protected readonly api = inject(StaffApi);
  protected readonly days = DAYS;
  protected readonly networks = ['interurban', 'international', 'airport'] as const;
  protected readonly stations = toSignal(this.api.stations().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly edits: Record<number, RouteDraft> = {};
  protected readonly schedules: Record<number, ScheduleDraft> = {};
  protected readonly list = rxResource({
    stream: () => this.api.routes(),
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected draft = { origin: null as number | null, destination: null as number | null, network: 'interurban', price: 1000, capacity: 50 };

  /** Brouillon d'édition d'une ligne, créé à son premier affichage : une saisie en cours n'est pas écrasée. */
  protected edit(route: Route): RouteDraft {
    return (this.edits[route.id] ??= { price: Number(route.price), capacity: route.capacity, active: route.active });
  }

  protected schedule(route: Route): ScheduleDraft {
    return (this.schedules[route.id] ??= emptySchedule());
  }

  protected save(route: Route): void {
    const draft = this.edits[route.id];
    this.run(this.api.updateRoute(route.id, { price: draft.price, capacity: draft.capacity, active: draft.active }));
  }

  protected addSchedule(route: Route): void {
    const draft = this.schedules[route.id];
    const days = DAYS.filter((day) => draft.days[day - 1]);
    if (!days.length) {
      this.error.set('days-required');
      return;
    }
    this.run(this.api.addSchedule(route.id, { days, departs_at: draft.departs_at, last_departure: draft.last_departure || null }), () => {
      this.schedules[route.id] = emptySchedule();
    });
  }

  protected create(): void {
    if (!this.draft.origin || !this.draft.destination) {
      return;
    }
    this.run(
      this.api.createRoute({
        origin_station_id: this.draft.origin,
        destination_station_id: this.draft.destination,
        network: this.draft.network,
        price: this.draft.price,
        capacity: this.draft.capacity,
      }),
    );
  }

  protected run(action: Observable<Route>, done?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    action.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (route) => {
        this.edits[route.id] = { price: Number(route.price), capacity: route.capacity, active: route.active };
        done?.();
        this.list.reload();
      },
      error: (error: unknown) => this.error.set(errorCode(error)),
    });
  }
}

export function emptySchedule(): ScheduleDraft {
  return { days: [false, false, false, false, false, false, false], departs_at: '', last_departure: '' };
}
