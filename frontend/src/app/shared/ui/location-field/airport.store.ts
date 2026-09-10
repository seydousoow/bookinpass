// noinspection ES6PreferShortImport

import { afterNextRender, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Airport } from '../../models/place.model';

/**
 * Référentiel des points de départ / d'arrivée.
 *
 * FIXTURE : le jeu de données est servi depuis `airports.json` en
 * attendant le point d'entrée backend. Le composant appelant n'en sait rien —
 * quand le contrat existera, seule cette classe changera.
 *
 * Le chargement passe par `afterNextRender` : il ne s'exécute jamais au rendu
 * serveur (plan §10.3).
 */
@Injectable({ providedIn: 'root' })
export class AirportStore {
  readonly airports = signal<readonly Airport[]>([]);
  readonly loading = signal(false);
  private readonly http = inject(HttpClient);

  constructor() {
    afterNextRender(() => this.load());
  }

  private load(): void {
    if (this.airports().length || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.http.get<Airport[]>('/airports.json').subscribe({
      next: (rows) => {
        this.airports.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
