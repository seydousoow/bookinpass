import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Session } from '@core/session';

/** Nouveau mot de passe : obligatoire après un mot de passe provisoire, possible à tout moment. */
@Component({
  selector: 'bo-password',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <main *transloco="let t" class="flex min-h-screen items-center justify-center p-4">
      <form (ngSubmit)="submit()" class="bo-card w-full max-w-sm space-y-4">
        <div>
          <h1 class="text-xl font-bold">{{ t('password.title') }}</h1>
          @if (session.user()?.must_change_password) {
            <p class="text-muted text-sm">{{ t('password.required') }}</p>
          }
          <p class="text-muted text-xs">{{ t('password.rules') }}</p>
        </div>
        <label class="block">
          <span class="bo-label">{{ t('password.current') }}</span>
          <input [(ngModel)]="current" autocomplete="current-password" class="bo-input" name="current" required type="password"/>
        </label>
        <label class="block">
          <span class="bo-label">{{ t('password.new') }}</span>
          <input [(ngModel)]="next" autocomplete="new-password" class="bo-input" minlength="10" name="next" required type="password"/>
        </label>
        <label class="block">
          <span class="bo-label">{{ t('password.confirm') }}</span>
          <input [(ngModel)]="confirm" autocomplete="new-password" class="bo-input" name="confirm" required type="password"/>
        </label>
        @if (error(); as code) {
          <p class="text-state-error text-sm" role="alert">{{ t('errors.' + code) }}</p>
        }
        <button [disabled]="busy() || !current || !next" class="bo-btn-primary w-full" type="submit">{{ t('password.submit') }}</button>
      </form>
    </main>
  `,
})
export class PasswordPage {
  protected current = '';
  protected next = '';
  protected confirm = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly session = inject(Session);
  private readonly api = inject(StaffApi);
  private readonly router = inject(Router);

  protected submit(): void {
    if (this.next !== this.confirm) {
      this.error.set('password-mismatch');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .changePassword(this.current, this.next)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (user) => {
          this.session.set(user);
          void this.router.navigate(['/']);
        },
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
