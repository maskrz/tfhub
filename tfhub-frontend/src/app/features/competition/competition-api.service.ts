import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ChangeCompetitionEventStatusRequest,
  CompetitionEvent,
  CompetitionResult,
  CompetitionSyncMode,
  CompetitionSyncStatus,
  GroupCompetitionSyncResponse,
  SaveCompetitionEventRequest,
  SaveCompetitionResultRequest,
} from './competition.model';

@Injectable({ providedIn: 'root' })
export class CompetitionApiService {
  private readonly http = inject(HttpClient);

  getEvents(): Observable<CompetitionEvent[]> {
    return this.http.get<CompetitionEvent[]>('/api/competition-events');
  }

  createEvent(request: SaveCompetitionEventRequest): Observable<CompetitionEvent> {
    return this.http.post<CompetitionEvent>('/api/competition-events', request);
  }

  updateEvent(id: number, request: SaveCompetitionEventRequest): Observable<CompetitionEvent> {
    return this.http.put<CompetitionEvent>(`/api/competition-events/${id}`, request);
  }

  changeEventStatus(id: number, request: ChangeCompetitionEventStatusRequest): Observable<CompetitionEvent> {
    return this.http.patch<CompetitionEvent>(`/api/competition-events/${id}/status`, request);
  }

  getAthleteResults(athleteId: number): Observable<CompetitionResult[]> {
    return this.http.get<CompetitionResult[]>(`/api/athletes/${athleteId}/competition-results`);
  }

  createResult(athleteId: number, request: SaveCompetitionResultRequest): Observable<CompetitionResult> {
    return this.http.post<CompetitionResult>(`/api/athletes/${athleteId}/competition-results`, request);
  }

  updateResult(id: number, request: SaveCompetitionResultRequest): Observable<CompetitionResult> {
    return this.http.put<CompetitionResult>(`/api/competition-results/${id}`, request);
  }

  deleteResult(id: number): Observable<void> {
    return this.http.delete<void>(`/api/competition-results/${id}`);
  }

  startAthleteSync(athleteId: number, mode: CompetitionSyncMode): Observable<CompetitionSyncStatus> {
    return this.http.post<CompetitionSyncStatus>(`/api/athletes/${athleteId}/competition-sync`, { mode });
  }

  getAthleteSyncStatus(athleteId: number): Observable<CompetitionSyncStatus> {
    return this.http.get<CompetitionSyncStatus>(`/api/athletes/${athleteId}/competition-sync-status`);
  }

  getSyncStatus(runId: number): Observable<CompetitionSyncStatus> {
    return this.http.get<CompetitionSyncStatus>(`/api/competition-syncs/${runId}`);
  }

  startGroupSync(groupId: number, mode: CompetitionSyncMode): Observable<GroupCompetitionSyncResponse> {
    return this.http.post<GroupCompetitionSyncResponse>(`/api/training-groups/${groupId}/competition-sync`, { mode });
  }
}
