import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@core/config/app-config.token';
import { IStayDestination } from '@shared/models/stay.model';

/** Destinations d'hébergement : villes GeoNames servies par le backend. */
@Injectable({ providedIn: 'root' })
export class StayDestinationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/stays/destinations`;

  search(query: string): Observable<IStayDestination[]> {
    return this.http.get<IStayDestination[]>(this.url, { params: { q: query, limit: 10 } });
  }

  find(id: string): Observable<IStayDestination> {
    return this.http.get<IStayDestination>(`${this.url}/${encodeURIComponent(id)}`);
  }
}

/** Identifiant GeoNames bien formé : une recherche mémorisée avant leur arrivée (code IATA) est ignorée. */
export function isDestinationId(value: string | null | undefined): value is string {
  return !!value && /^[1-9]\d{0,8}$/.test(value);
}
