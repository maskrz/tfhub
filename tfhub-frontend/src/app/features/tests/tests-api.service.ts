import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TestResponse } from './test.model';

@Injectable({ providedIn: 'root' })
export class TestsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<TestResponse[]> {
    return this.http.post<TestResponse[]>('/api/tests/search', {});
  }
}
