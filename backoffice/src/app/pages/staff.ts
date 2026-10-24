import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, Observable, of } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi, StaffUserForm } from '@core/api';
import { Session } from '@core/session';
import { temporaryPassword } from '@core/password';
import { dakarDateTime } from '@core/dakar';
import { StaffRole, StaffUser } from '@core/models';

interface UserDraft {
  full_name: string;
  phone: string;
  role: StaffRole;
  station_id: number | null;
  active: boolean;
}

/**
 * Personnel : création (mot de passe provisoire à transmettre de vive voix), rôle, gare, désactivation, nouveau mot
 * de passe provisoire. Un manager ne gère que chefs de gare et contrôleurs.
 */
@Component({
  selector: 'bo-staff',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <section *transloco="let t" class="space-y-4">
      <h1 class="text-xl font-bold">{{ t('staff.title') }}</h1>
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }
      @if (issued(); as p) {
        <p class="bg-state-warning-soft rounded-card p-3 text-sm" role="status">
          {{ t('staff.issued', {username: p.username}) }} <span class="font-mono text-base font-bold">{{ p.password }}</span>
        </p>
      }

      <form (ngSubmit)="create()" class="bo-card grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <h2 class="font-bold sm:col-span-2 lg:col-span-3">{{ t('staff.new') }}</h2>
        <label><span class="bo-label">{{ t('staff.username') }}</span>
          <input [(ngModel)]="form.username" autocapitalize="none" class="bo-input" maxlength="50" name="username" pattern="[a-z0-9._-]+" required/></label>
        <label><span class="bo-label">{{ t('staff.full-name') }}</span><input [(ngModel)]="form.full_name" class="bo-input" name="full_name" required/></label>
        <label><span class="bo-label">{{ t('staff.phone') }}</span><input [(ngModel)]="form.phone" class="bo-input" name="phone" placeholder="+221…" type="tel"/></label>
        <label><span class="bo-label">{{ t('staff.role') }}</span>
          <select [(ngModel)]="form.role" class="bo-input" name="role">
            @for (role of roles(); track role) {
              <option [value]="role">{{ t('roles.' + role) }}</option>
            }
          </select>
        </label>
        @if (needsStation(form.role)) {
          <label><span class="bo-label">{{ t('common.station') }}</span>
            <select [(ngModel)]="form.station_id" class="bo-input" name="station" required>
              @for (s of stations(); track s.id) {
                <option [ngValue]="s.id">{{ s.name }} ({{ s.place }})</option>
              }
            </select>
          </label>
        }
        <div class="flex items-end">
          <button [disabled]="busy() || !form.username || !form.full_name" class="bo-btn-primary" type="submit">{{ t('staff.create') }}</button>
        </div>
      </form>

      @if (list.isLoading() && !list.hasValue()) {
        <p class="text-muted">{{ t('common.loading') }}</p>
      }
      <ul class="grid gap-2">
        @for (u of list.value() ?? []; track u.id) {
          <li [class.opacity-60]="!u.active" class="bo-card space-y-3">
            <p class="flex flex-wrap justify-between gap-2 text-sm">
              <span><span class="font-semibold">{{ u.full_name }}</span> · <span class="font-mono">{{ u.username }}</span>
                @if (u.must_change_password) {
                  <span class="text-state-warning"> · {{ t('staff.temporary') }}</span>
                }
              </span>
              <span class="text-muted">{{ u.last_login_at ? t('staff.last-login', {time: dateTime(u.last_login_at)}) : t('staff.never') }}</span>
            </p>
            @if (u.id !== session.user()?.id) {
              <div class="flex flex-wrap items-end gap-3">
                <label><span class="bo-label">{{ t('staff.full-name') }}</span><input [(ngModel)]="edit(u).full_name" class="bo-input"/></label>
                <label><span class="bo-label">{{ t('staff.phone') }}</span><input [(ngModel)]="edit(u).phone" class="bo-input w-40" type="tel"/></label>
                <label><span class="bo-label">{{ t('staff.role') }}</span>
                  <select [(ngModel)]="edit(u).role" class="bo-input">
                    @for (role of roles(); track role) {
                      <option [value]="role">{{ t('roles.' + role) }}</option>
                    }
                  </select>
                </label>
                @if (needsStation(edit(u).role)) {
                  <label><span class="bo-label">{{ t('common.station') }}</span>
                    <select [(ngModel)]="edit(u).station_id" class="bo-input">
                      @for (s of stations(); track s.id) {
                        <option [ngValue]="s.id">{{ s.name }} ({{ s.place }})</option>
                      }
                    </select>
                  </label>
                }
                <label class="flex min-h-11 items-center gap-2 text-sm"><input [(ngModel)]="edit(u).active" type="checkbox"/> {{ t('staff.active') }}</label>
                <button (click)="save(u)" [disabled]="busy()" class="bo-btn-outline" type="button">{{ t('common.save') }}</button>
                <button (click)="reset(u)" [disabled]="busy()" class="bo-btn-outline" type="button">{{ t('staff.reset') }}</button>
              </div>
            } @else {
              <p class="text-muted text-xs">{{ t('roles.' + u.role) }} · {{ t('staff.yourself') }}</p>
            }
          </li>
        }
      </ul>
    </section>
  `,
})
export class StaffPage {
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  protected readonly stations = toSignal(this.api.stations().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly list = rxResource({ stream: () => this.api.users() });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Dernier mot de passe provisoire donné : affiché une seule fois, à transmettre à la personne. */
  protected readonly issued = signal<{ username: string; password: string } | null>(null);
  protected readonly drafts: Record<number, UserDraft> = {};
  protected form: StaffUserForm = { username: '', full_name: '', phone: null, role: 'inspector', station_id: null };
  protected readonly dateTime = dakarDateTime;

  /** Un manager ne donne que les rôles de gare. */
  protected roles(): StaffRole[] {
    return this.session.has('admin') ? ['admin', 'manager', 'station_chief', 'inspector'] : ['station_chief', 'inspector'];
  }

  protected needsStation(role: StaffRole): boolean {
    return role === 'station_chief' || role === 'inspector';
  }

  protected edit(user: StaffUser): UserDraft {
    return (this.drafts[user.id] ??= {
      full_name: user.full_name,
      phone: user.phone ?? '',
      role: user.role,
      station_id: user.station?.id ?? null,
      active: user.active,
    });
  }

  protected create(): void {
    const password = temporaryPassword();
    const username = (this.form.username ?? '').trim().toLowerCase();
    this.run(this.api.createUser({ ...this.form, username, phone: this.form.phone || null, temporary_password: password }), () => {
      this.issued.set({ username, password });
      this.form = { username: '', full_name: '', phone: null, role: 'inspector', station_id: null };
    });
  }

  protected save(user: StaffUser): void {
    const draft = this.drafts[user.id];
    this.run(
      this.api.updateUser(user.id, {
        full_name: draft.full_name,
        phone: draft.phone || null,
        role: draft.role,
        station_id: this.needsStation(draft.role) ? draft.station_id : null,
        active: draft.active,
      }),
    );
  }

  protected reset(user: StaffUser): void {
    const password = temporaryPassword();
    this.run(this.api.resetPassword(user.id, password), () => this.issued.set({ username: user.username, password }));
  }

  private run(action: Observable<StaffUser>, done?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    action.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (user) => {
        delete this.drafts[user.id];
        done?.();
        this.list.reload();
      },
      error: (error: unknown) => this.error.set(errorCode(error)),
    });
  }
}
