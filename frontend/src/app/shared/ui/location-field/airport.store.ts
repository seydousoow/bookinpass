// noinspection ES6PreferShortImport

import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IAirport } from '../../models/place.model';
import { APP_CONFIG } from '../../../core/config/app-config.token';

/**
 * Référentiel des points de départ / d'arrivée.
 *
 * Le jeu de données vient du backend (`GET /api/airports`, mis en cache côté
 * serveur). Le composant appelant n'en sait rien : seule cette classe connaît l'URL.
 */
@Injectable({ providedIn: 'root' })
export class AirportStore {
  readonly airports = signal<readonly IAirport[]>([]);
  readonly loading = signal(false);
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;

  constructor() {
    this.load();
  }

  private load(): void {
    if (this.airports().length || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.http.get<IAirport[]>(`${this.apiBaseUrl}/airports`).subscribe({
      next: (rows) => {
        this.airports.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
