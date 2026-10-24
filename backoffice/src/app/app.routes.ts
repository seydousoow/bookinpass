import { Routes } from '@angular/router';
import { passwordGuard, roleGuard, signedInGuard } from '@core/session';

/** Pages du back-office ; réseau et personnel réservés aux responsables (admin, manager). */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login').then((m) => m.LoginPage) },
  { path: 'password', canActivate: [passwordGuard], loadComponent: () => import('./pages/password').then((m) => m.PasswordPage) },
  {
    path: '',
    canActivate: [signedInGuard],
    loadComponent: () => import('./pages/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./pages/departures').then((m) => m.DeparturesPage) },
      { path: 'departures/:id', loadComponent: () => import('./pages/departure').then((m) => m.DeparturePage) },
      { path: 'scan', loadComponent: () => import('./pages/scan').then((m) => m.ScanPage) },
      {
        path: 'luggage',
        canActivate: [roleGuard],
        data: { roles: ['admin', 'manager', 'station_chief'] },
        loadComponent: () => import('./pages/luggage').then((m) => m.LuggagePage),
      },
      { path: 'incidents', loadComponent: () => import('./pages/incidents').then((m) => m.IncidentsPage) },
      {
        path: 'routes',
        canActivate: [roleGuard],
        data: { roles: ['admin', 'manager'] },
        loadComponent: () => import('./pages/routes').then((m) => m.RoutesPage),
      },
      {
        path: 'staff',
        canActivate: [roleGuard],
        data: { roles: ['admin', 'manager'] },
        loadComponent: () => import('./pages/staff').then((m) => m.StaffPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
