import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AthleteResponse,
  ChangeAthleteStatusRequest,
  CreateAthleteRequest,
  UpdateAthleteRequest,
} from './athlete.model';
import { AthleteProfileResult, AthleteResultHistoryFilters } from './athlete-profile.model';
import { TestResponse } from '../tests/test.model';

@Injectable({ providedIn: 'root' })
export class AthletesApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<AthleteResponse[]> {
    return this.http.post<AthleteResponse[]>('/api/athletes/search', {});
  }

  getById(id: number): Observable<AthleteResponse> {
    return this.http.get<AthleteResponse>(`/api/athletes/${id}`);
  }

  getTestsWithResults(id: number): Observable<TestResponse[]> {
    return this.http.get<TestResponse[]>(`/api/athletes/${id}/tests`);
  }

  getResultHistory(id: number, filters: AthleteResultHistoryFilters): Observable<AthleteProfileResult[]> {
    let params = new HttpParams();
    for (const testId of filters.testIds) {
      params = params.append('testIds', testId);
    }
    if (filters.fromDate) params = params.set('fromDate', filters.fromDate);
    if (filters.toDate) params = params.set('toDate', filters.toDate);
    return this.http.get<AthleteProfileResult[]>(`/api/athletes/${id}/results`, { params });
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
