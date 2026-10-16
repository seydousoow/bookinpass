import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { APP_CONFIG } from '@core/config/app-config.token';
import { IStayQuote, IStayQuoteResponse, IStayResult, IStayResultResponse, IStayReviewsResponse } from '@shared/models';

/** Appels de la réservation d'hébergement hors chargement initial (qui passe par `httpResource`). */
@Injectable({ providedIn: 'root' })
export class StayBookingService {
  private readonly http = inject(HttpClient);
  private readonly staysUrl = `${inject(APP_CONFIG).apiBaseUrl}/stays`;

  /** Chambres et tarifs à jour d'un résultat (revalidation). */
  getRates(resultId: string): Observable<IStayResult> {
    return this.http.get<IStayResultResponse>(`${this.staysUrl}/results/${encodeURIComponent(resultId)}/rates`).pipe(map((r) => r.data));
  }

  /** Vérification finale du tarif : disponibilité et prix définitif. */
  createQuote(rateId: string): Observable<IStayQuote> {
    return this.http.post<IStayQuoteResponse>(`${this.staysUrl}/quotes`, { rate_id: rateId }).pipe(map((r) => r.data));
  }

  reviews(accommodationId: string, after: string | null, limit: number): Observable<IStayReviewsResponse> {
    const params: Record<string, string | number> = { limit };
    if (after) {
      params['after'] = after;
    }
    return this.http.get<IStayReviewsResponse>(`${this.staysUrl}/accommodations/${encodeURIComponent(accommodationId)}/reviews`, { params });
  }
}
