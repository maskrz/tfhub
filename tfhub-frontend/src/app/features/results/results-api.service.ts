import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateMultipleResultsRequest,
  CreateMultipleResultsResponse,
  CreateResultRequest,
  ResultResponse,
  UpdateResultRequest,
} from './result.model';

@Injectable({ providedIn: 'root' })
export class ResultsApiService {
  private readonly http = inject(HttpClient);

  getAll(groupIds: number[] = []): Observable<ResultResponse[]> {
    let params = new HttpParams();
    for (const groupId of groupIds) params = params.append('groupIds', groupId);
    return this.http.get<ResultResponse[]>('/api/results', { params });
  }

  createResult(request: CreateResultRequest): Observable<ResultResponse> {
    return this.http.post<ResultResponse>('/api/results', request);
  }

  updateResult(id: number, request: UpdateResultRequest): Observable<ResultResponse> {
    return this.http.put<ResultResponse>(`/api/results/${id}`, request);
  }

  createMultipleResults(request: CreateMultipleResultsRequest): Observable<CreateMultipleResultsResponse> {
    return this.http.post<CreateMultipleResultsResponse>('/api/results/batch', request);
  }

  deleteResult(id: number): Observable<void> {
    return this.http.delete<void>(`/api/results/${id}`);
  }
}
