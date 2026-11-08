import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClubRecords, DashboardOverview } from './statistics.model';

@Injectable({ providedIn: 'root' })
export class StatisticsApiService {
  private readonly http = inject(HttpClient);

  getDashboardOverview(): Observable<DashboardOverview> {
    return this.http.get<DashboardOverview>('/api/statistics/dashboard');
  }

  getClubRecords(testId: number, season: number | null): Observable<ClubRecords> {
    let params = new HttpParams().set('testId', testId);
    if (season !== null) params = params.set('season', season);
    return this.http.get<ClubRecords>('/api/statistics/records', { params });
  }
}
