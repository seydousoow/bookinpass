import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { Session } from '@core/session';

/** Connexion du personnel : identifiant et mot de passe ; 5 échecs de suite bloquent le compte 15 minutes. */
@Component({
  selector: 'bo-login',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <main *transloco="let t" class="flex min-h-screen items-center justify-center p-4">
      <form (ngSubmit)="submit()" class="bo-card w-full max-w-sm space-y-4">
        <div>
          <h1 class="text-xl font-bold">{{ t('app.title') }}</h1>
          <p class="text-muted text-sm">{{ t('login.subtitle') }}</p>
        </div>
        <label class="block">
          <span class="bo-label">{{ t('login.username') }}</span>
          <input [(ngModel)]="username" autocapitalize="none" autocomplete="username" class="bo-input" name="username" required/>
        </label>
        <label class="block">
          <span class="bo-label">{{ t('login.password') }}</span>
          <input [(ngModel)]="password" autocomplete="current-password" class="bo-input" name="password" required type="password"/>
        </label>
        @if (error(); as code) {
          <p class="text-state-error text-sm" role="alert">{{ t('errors.' + code) }}</p>
        }
        <button [disabled]="busy() || !username || !password" class="bo-btn-primary w-full" type="submit">{{ t('login.submit') }}</button>
      </form>
    </main>
  `,
})
export class LoginPage {
  protected username = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly api = inject(StaffApi);
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .login(this.username, this.password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (user) => {
          this.session.set(user);
          this.password = '';
          void this.router.navigate([user.must_change_password ? '/password' : '/']);
        },
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
