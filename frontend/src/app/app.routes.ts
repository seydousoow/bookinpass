import { Routes } from '@angular/router';
import { flightSearchParamsGuard } from '@features/flight/search/search-params';
import { staySearchParamsGuard } from '@features/stay/search/stay-search-params';

/** Une route paresseuse par feature — le socle seul reste dans le bundle initial. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'flight' },
  {
    path: 'flight',
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('@features/home/home').then((m) => m.Home),
        children: [{ path: '', loadComponent: () => import('@features/flight/search-form/flight-search-form').then((m) => m.FlightSearchForm) }],
      },
      {
        path: 'search',
        loadComponent: () => import('@features/flight/search/flight-search').then((m) => m.FlightSearch),
        canActivate: [flightSearchParamsGuard],
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
      },
      {
        path: ':offerId',
        loadComponent: () => import('@features/flight/details/flight-offer').then((o) => o.FlightOffer),
      },
    ],
  },
  {
    path: 'stays',
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('@features/home/home').then((m) => m.Home),
        children: [{ path: '', loadComponent: () => import('@features/stay/search-form/stay-search-form').then((m) => m.StaySearchForm) }],
      },
      {
        path: 'search',
        loadComponent: () => import('@features/stay/search/stay-search').then((m) => m.StaySearch),
        canActivate: [staySearchParamsGuard],
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
      },
      {
        path: ':resultId',
        loadComponent: () => import('@features/stay/details/stay-details').then((m) => m.StayDetails),
      },
    ],
  },
  {
    path: 'ui-kit',
    loadComponent: () => import('@features/ui-kit/ui-kit.page').then((m) => m.UiKitPage),
    title: 'Design system — Book In Pass',
  },
  { path: '**', redirectTo: '' },
];
