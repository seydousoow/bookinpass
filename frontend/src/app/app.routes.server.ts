import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Mode de rendu par route — plan §10.2. Tout ce qui dépend d'une recherche,
 * d'un paiement ou d'une session reste en client side.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Client },
  { path: 'search', renderMode: RenderMode.Client },
  { path: 'flight', renderMode: RenderMode.Client },
  { path: 'flight/**', renderMode: RenderMode.Client },
  { path: 'ui-kit', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Server },
];
