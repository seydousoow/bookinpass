import { Routes } from '@angular/router';

/** Une route paresseuse par feature — le socle seul reste dans le bundle initial. */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('@features/home/home.page').then((m) => m.HomePage),
    title: 'Book In Pass — Vols, séjours et activités',
  },
  {
    path: 'ui-kit',
    loadComponent: () => import('@features/ui-kit/ui-kit.page').then((m) => m.UiKitPage),
    title: 'Design system — Book In Pass',
  },
  { path: '**', redirectTo: '' },
];
