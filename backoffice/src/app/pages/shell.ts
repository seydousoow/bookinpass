import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { Session } from '@core/session';
import { StaffRole } from '@core/models';
import { saveLang } from '@core/lang';

interface NavEntry {
  readonly key: string;
  readonly route: string;
  readonly roles: readonly StaffRole[];
}

const ALL: readonly StaffRole[] = ['admin', 'manager', 'station_chief', 'inspector'];

/** En-tête du back-office : sections permises par le rôle, gare de travail, langue, déconnexion. */
@Component({
  selector: 'bo-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoDirective],
  template: `
    <ng-container *transloco="let t">
      <header class="bg-brand-dark text-onbrand">
        <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <div>
            <p class="font-bold">{{ t('app.title') }}</p>
            <p class="text-xs opacity-80">
              {{ session.user()?.full_name }} · {{ t('roles.' + session.role()) }}
              @if (session.user()?.station; as station) {
                · {{ station.name }}
              }
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button (click)="toggleLang()" class="bo-focusable rounded-button min-h-11 px-3 text-sm font-semibold hover:bg-white/10" type="button">
              {{ t('app.other-lang') }}
            </button>
            <button (click)="logout()" class="bo-focusable rounded-button min-h-11 px-3 text-sm font-semibold hover:bg-white/10" type="button">
              {{ t('app.logout') }}
            </button>
          </div>
        </div>
        <nav [attr.aria-label]="t('app.nav')" class="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-2">
          @for (entry of entries(); track entry.key) {
            <a #link="routerLinkActive" [attr.aria-current]="link.isActive ? 'page' : null" [routerLinkActiveOptions]="{ exact: entry.route === '/' }"
               [routerLink]="entry.route" class="bo-focusable rounded-t-card flex min-h-11 shrink-0 items-center px-4 text-sm font-semibold"
               routerLinkActive="bg-surface-page text-brand-dark">
              {{ t('nav.' + entry.key) }}
            </a>
          }
        </nav>
      </header>
      <main class="mx-auto max-w-6xl px-4 py-4">
        <router-outlet/>
      </main>
    </ng-container>
  `,
})
export class Shell {
  protected readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly nav: readonly NavEntry[] = [
    { key: 'departures', route: '/', roles: ALL },
    { key: 'scan', route: '/scan', roles: ALL },
    { key: 'luggage', route: '/luggage', roles: ['admin', 'manager', 'station_chief'] },
    { key: 'incidents', route: '/incidents', roles: ALL },
    { key: 'bookings', route: '/bookings', roles: ALL },
    { key: 'refunds', route: '/refunds', roles: ['admin', 'manager'] },
    { key: 'routes', route: '/routes', roles: ['admin', 'manager'] },
    { key: 'staff', route: '/staff', roles: ['admin', 'manager'] },
  ];
  protected readonly entries = computed(() => this.nav.filter((entry) => this.session.has(...entry.roles)));
  private readonly leaving = signal(false);

  protected toggleLang(): void {
    const lang = this.transloco.getActiveLang() === 'fr' ? 'en' : 'fr';
    this.transloco.setActiveLang(lang);
    saveLang(lang);
  }

  protected logout(): void {
    if (this.leaving()) {
      return;
    }
    this.leaving.set(true);
    this.session.logout().subscribe(() => void this.router.navigate(['/login']));
  }
}
