import { Routes } from '@angular/router';

/** Une route paresseuse par feature — le socle seul reste dans le bundle initial. */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('@features/home/home').then((m) => m.Home),
  },
  {
    path: 'search',
    loadComponent: () => import('@features/flight/search/flight-search').then((m) => m.FlightSearch),
  },
  {
    path: 'flight',
    children: [
      {
        path: ':offerId',
        loadComponent: () => import('@features/flight/details/flight-offer').then((o) => o.FlightOffer),
      },
      {
        path: '**', redirectTo: '/'
      }
    ],
  },
  {
    path: 'ui-kit',
    loadComponent: () => import('@features/ui-kit/ui-kit.page').then((m) => m.UiKitPage),
    title: 'Design system — Book In Pass',
  },
  { path: '**', redirectTo: '' },
];
