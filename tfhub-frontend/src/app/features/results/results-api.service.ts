import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateMultipleResultsRequest,
  CreateMultipleResultsResponse,
  CreateResultRequest,
  ResultResponse,
} from './result.model';

@Injectable({ providedIn: 'root' })
export class ResultsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<ResultResponse[]> {
    return this.http.get<ResultResponse[]>('/api/results');
  }

  createResult(request: CreateResultRequest): Observable<ResultResponse> {
    return this.http.post<ResultResponse>('/api/results', request);
  }

  createMultipleResults(request: CreateMultipleResultsRequest): Observable<CreateMultipleResultsResponse> {
    return this.http.post<CreateMultipleResultsResponse>('/api/results/batch', request);
  }
}
