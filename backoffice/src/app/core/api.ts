import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from './config';
import { Departure, DepartureDetail, Incident, IncidentType, Luggage, Route, StaffRole, StaffUser, Station, TicketCheck } from './models';

/** Code d'erreur stable renvoyé par l'API (`forbidden`, `capacity-too-low`…), ou `network` sans réponse. */
export function errorCode(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    return (error.error as { code?: string } | null)?.code ?? (error.status === 0 ? 'network' : 'unexpected');
  }
  return 'unexpected';
}

export interface StaffUserForm {
  username?: string;
  full_name: string;
  phone: string | null;
  role: StaffRole;
  station_id: number | null;
  temporary_password?: string;
  active?: boolean;
}

export interface LuggageForm {
  sender_name: string;
  sender_phone: string;
  sender_id_number: string | null;
  receiver_name: string;
  receiver_phone: string;
  description: string | null;
  weight_kg: number;
  price: number;
}

/** Appels du back-office (`/staff/**`). Le cookie de session accompagne chaque requête (intercepteur). */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/staff`;

  me(): Observable<StaffUser> {
    return this.http.get<StaffUser>(`${this.url}/auth/me`);
  }

  login(username: string, password: string): Observable<StaffUser> {
    return this.http.post<StaffUser>(`${this.url}/auth/login`, { username, password });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/auth/logout`, null);
  }

  changePassword(current: string, next: string): Observable<StaffUser> {
    return this.http.post<StaffUser>(`${this.url}/auth/password`, { current_password: current, new_password: next });
  }

  stations(): Observable<Station[]> {
    return this.http.get<Station[]>(`${this.url}/stations`);
  }

  departures(date: string, stationId: number | null): Observable<Departure[]> {
    const params: Record<string, string> = { date };
    if (stationId !== null) {
      params['station_id'] = String(stationId);
    }
    return this.http.get<Departure[]>(`${this.url}/departures`, { params });
  }

  departure(id: number): Observable<DepartureDetail> {
    return this.http.get<DepartureDetail>(`${this.url}/departures/${id}`);
  }

  markDeparted(id: number): Observable<DepartureDetail> {
    return this.http.post<DepartureDetail>(`${this.url}/departures/${id}/departed`, null);
  }

  markArrived(id: number): Observable<DepartureDetail> {
    return this.http.post<DepartureDetail>(`${this.url}/departures/${id}/arrived`, null);
  }

  resize(id: number, capacity: number): Observable<DepartureDetail> {
    return this.http.patch<DepartureDetail>(`${this.url}/departures/${id}`, { capacity });
  }

  checkTicket(code: string): Observable<TicketCheck> {
    return this.http.get<TicketCheck>(`${this.url}/tickets/${encodeURIComponent(code)}`);
  }

  scanTicket(code: string): Observable<TicketCheck> {
    return this.http.post<TicketCheck>(`${this.url}/tickets/${encodeURIComponent(code)}/scan`, null);
  }

  registerLuggage(departureId: number, form: LuggageForm): Observable<Luggage> {
    return this.http.post<Luggage>(`${this.url}/departures/${departureId}/luggage`, form);
  }

  luggage(date: string, stationId: number | null): Observable<Luggage[]> {
    const params: Record<string, string> = { date };
    if (stationId !== null) {
      params['station_id'] = String(stationId);
    }
    return this.http.get<Luggage[]>(`${this.url}/luggage`, { params });
  }

  findLuggage(code: string): Observable<Luggage> {
    return this.http.get<Luggage>(`${this.url}/luggage/${encodeURIComponent(code)}`);
  }

  deliverLuggage(code: string): Observable<Luggage> {
    return this.http.post<Luggage>(`${this.url}/luggage/${encodeURIComponent(code)}/delivered`, null);
  }

  incidents(open: boolean, stationId: number | null): Observable<Incident[]> {
    const params: Record<string, string> = { open: String(open) };
    if (stationId !== null) {
      params['station_id'] = String(stationId);
    }
    return this.http.get<Incident[]>(`${this.url}/incidents`, { params });
  }

  reportIncident(report: { departure_id: number | null; route_id: number | null; type: IncidentType; description: string }): Observable<Incident> {
    return this.http.post<Incident>(`${this.url}/incidents`, report);
  }

  resolveIncident(id: number, resolution: string): Observable<Incident> {
    return this.http.post<Incident>(`${this.url}/incidents/${id}/resolve`, { resolution });
  }

  routes(): Observable<Route[]> {
    return this.http.get<Route[]>(`${this.url}/routes`);
  }

  createRoute(route: { origin_station_id: number; destination_station_id: number; network: string; price: number; capacity: number }): Observable<Route> {
    return this.http.post<Route>(`${this.url}/routes`, route);
  }

  updateRoute(id: number, route: { price: number; capacity: number; active: boolean }): Observable<Route> {
    return this.http.patch<Route>(`${this.url}/routes/${id}`, route);
  }

  addSchedule(routeId: number, schedule: { days: number[]; departs_at: string; last_departure: string | null }): Observable<Route> {
    return this.http.post<Route>(`${this.url}/routes/${routeId}/schedules`, schedule);
  }

  deleteSchedule(id: number): Observable<Route> {
    return this.http.delete<Route>(`${this.url}/schedules/${id}`);
  }

  users(): Observable<StaffUser[]> {
    return this.http.get<StaffUser[]>(`${this.url}/users`);
  }

  createUser(form: StaffUserForm): Observable<StaffUser> {
    return this.http.post<StaffUser>(`${this.url}/users`, form);
  }

  updateUser(id: number, form: StaffUserForm): Observable<StaffUser> {
    return this.http.patch<StaffUser>(`${this.url}/users/${id}`, form);
  }

  resetPassword(id: number, temporaryPassword: string): Observable<StaffUser> {
    return this.http.post<StaffUser>(`${this.url}/users/${id}/password`, { temporary_password: temporaryPassword });
  }
}
