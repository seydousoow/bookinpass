import { computed, inject, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { IAirlineReference } from '@shared/models/airline.model';
import { APP_CONFIG } from '@core/config/app-config.token';

/**
 * Référentiel des compagnies aériennes (`GET /api/airlines`, mis en cache côté serveur).
 * Chargé une seule fois, au premier composant qui en a besoin.
 */
@Injectable({ providedIn: 'root' })
export class AirlineStore {
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  private readonly resource = httpResource<readonly IAirlineReference[]>(() => `${this.apiBaseUrl}/airlines`, { defaultValue: [] });

  /** Compagnies par code IATA. Vide tant que la liste n'est pas chargée (ou si elle a échoué). */
  readonly byCode = computed(() => new Map((this.resource.hasValue() ? this.resource.value() : []).map((a) => [a.iata, a])));
}
