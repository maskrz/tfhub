import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AthleteResponse,
  ChangeAthleteStatusRequest,
  CreateAthleteRequest,
  UpdateAthleteRequest,
} from './athlete.model';

@Injectable({ providedIn: 'root' })
export class AthletesApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<AthleteResponse[]> {
    return this.http.post<AthleteResponse[]>('/api/athletes/search', {});
  }

  createAthlete(request: CreateAthleteRequest): Observable<AthleteResponse> {
    return this.http.post<AthleteResponse>('/api/athletes', request);
  }

  updateAthlete(id: number, request: UpdateAthleteRequest): Observable<AthleteResponse> {
    return this.http.put<AthleteResponse>(`/api/athletes/${id}`, request);
  }

  changeStatus(id: number, request: ChangeAthleteStatusRequest): Observable<AthleteResponse> {
    return this.http.patch<AthleteResponse>(`/api/athletes/${id}/status`, request);
  }
}
