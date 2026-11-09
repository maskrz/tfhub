import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClubRecords, DashboardOverview } from './statistics.model';

@Injectable({ providedIn: 'root' })
export class StatisticsApiService {
  private readonly http = inject(HttpClient);

  getDashboardOverview(groupIds: number[] = []): Observable<DashboardOverview> {
    let params = new HttpParams();
    for (const groupId of groupIds) params = params.append('groupIds', groupId);
    return this.http.get<DashboardOverview>('/api/statistics/dashboard', { params });
  }

  getClubRecords(testId: number, season: number | null, groupIds: number[] = []): Observable<ClubRecords> {
    let params = new HttpParams().set('testId', testId);
    if (season !== null) params = params.set('season', season);
    for (const groupId of groupIds) params = params.append('groupIds', groupId);
    return this.http.get<ClubRecords>('/api/statistics/records', { params });
  }
}
