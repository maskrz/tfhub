import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ChangeTestStatusRequest,
  CreateTestRequest,
  TestResponse,
  UpdateTestRequest,
} from './test.model';

@Injectable({ providedIn: 'root' })
export class TestsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<TestResponse[]> {
    return this.http.post<TestResponse[]>('/api/tests/search', {});
  }

  createTest(request: CreateTestRequest): Observable<TestResponse> {
    return this.http.post<TestResponse>('/api/tests', request);
  }

  updateTest(id: number, request: UpdateTestRequest): Observable<TestResponse> {
    return this.http.put<TestResponse>(`/api/tests/${id}`, request);
  }

  changeStatus(id: number, request: ChangeTestStatusRequest): Observable<TestResponse> {
    return this.http.patch<TestResponse>(`/api/tests/${id}/status`, request);
  }
}
