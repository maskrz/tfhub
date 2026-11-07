import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ResultResponse } from './result.model';

@Injectable({ providedIn: 'root' })
export class ResultsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<ResultResponse[]> {
    return this.http.get<ResultResponse[]>('/api/results');
  }
}
