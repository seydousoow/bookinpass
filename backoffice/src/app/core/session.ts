import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, map, of, tap, throwError } from 'rxjs';
import { StaffApi } from './api';
import { StaffRole, StaffUser } from './models';

/** Membre du personnel connecté ; `undefined` tant que la session n'a pas été lue. */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(StaffApi);
  private readonly current = signal<StaffUser | null | undefined>(undefined);
  readonly user = this.current.asReadonly();
  readonly role = computed(() => this.current()?.role ?? null);
  /** Admin et manager : toutes les gares, réseau et personnel. */
  readonly manager = computed(() => this.role() === 'admin' || this.role() === 'manager');
  /** Chef de gare ou responsable : départs, bagages, incidents. */
  readonly chief = computed(() => this.manager() || this.role() === 'station_chief');

  /** Lit la session une fois (cookie HttpOnly : seul le serveur sait si elle est valable). */
  load(): Promise<StaffUser | null> {
    if (this.current() !== undefined) {
      return Promise.resolve(this.current() ?? null);
    }
    return firstValueFrom(
      this.api.me().pipe(
        catchError(() => of(null)),
        tap((user) => this.current.set(user)),
      ),
    );
  }

  set(user: StaffUser | null): void {
    this.current.set(user);
  }

  logout() {
    return this.api.logout().pipe(
      catchError(() => of(undefined)),
      tap(() => this.current.set(null)),
      map(() => undefined),
    );
  }

  has(...roles: StaffRole[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }
}

/** Le cookie de session part avec chaque appel à l'API (autre origine). */
export const credentialsInterceptor: HttpInterceptorFn = (request, next) => next(request.clone({ withCredentials: true }));

/** Session expirée ou fermée (401) : retour à la connexion. */
export const signedOutInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !request.url.endsWith('/auth/login') && !request.url.endsWith('/auth/me')) {
        session.set(null);
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};

/** Pages du back-office : connecté, mot de passe définitif. */
export const signedInGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const user = await inject(Session).load();
  if (!user) {
    return router.parseUrl('/login');
  }
  return user.must_change_password ? router.parseUrl('/password') : true;
};

/** Changement de mot de passe : connecté (mot de passe provisoire ou non). */
export const passwordGuard: CanActivateFn = async () => ((await inject(Session).load()) ? true : inject(Router).parseUrl('/login'));

/** Réservé aux rôles listés dans `data.roles`. */
export const roleGuard: CanActivateFn = (route) => {
  const roles = (route.data['roles'] as StaffRole[] | undefined) ?? [];
  return inject(Session).has(...roles) ? true : inject(Router).parseUrl('/');
};
