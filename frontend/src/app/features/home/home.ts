import { Component, inject } from '@angular/core';
import { isActive, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Card } from '@shared/ui/card/card';

interface IHomeTab {
  readonly key: 'flights' | 'stays' | 'bus' | 'manage';
  readonly route: string;
}

/**
 * Accueil : un formulaire de recherche par produit. Chaque onglet est une route (`/flight` pour les vols, `/stays` pour
 * l'hébergement, `/bus` pour le car) dont le formulaire est fourni par sa feature via `app.routes.ts` — une feature n'en importe
 * jamais une autre.
 */
@Component({
  selector: 'bip-home-page',
  imports: [TranslocoDirective, Card, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './home.html',
})
export class Home {
  protected readonly tabs: readonly IHomeTab[] = [
    { key: 'flights', route: '/flight' },
    { key: 'stays', route: '/stays' },
    { key: 'bus', route: '/bus' },
    { key: 'manage', route: '/flight/manage' },
  ];
  /** Premier onglet actif : le coin haut gauche de la carte se raccorde à lui, sans arrondi. */
  protected readonly firstTabActive = isActive(this.tabs[0].route, inject(Router), { paths: 'exact' });
}
