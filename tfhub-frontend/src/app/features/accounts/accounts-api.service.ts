import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AccountResponse,
  ChangeAccountStatusRequest,
  CreateAccountRequest,
  ResetAccountPasswordRequest,
  UpdateAccountRequest,
} from './account.model';

@Injectable({ providedIn: 'root' })
export class AccountsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts');
  }

  createAccount(request: CreateAccountRequest): Observable<AccountResponse> {
    return this.http.post<AccountResponse>('/api/accounts', request);
  }

  updateAccount(id: number, request: UpdateAccountRequest): Observable<AccountResponse> {
    return this.http.put<AccountResponse>(`/api/accounts/${id}`, request);
  }

  changeStatus(id: number, request: ChangeAccountStatusRequest): Observable<AccountResponse> {
    return this.http.patch<AccountResponse>(`/api/accounts/${id}/status`, request);
  }

  resetPassword(id: number, request: ResetAccountPasswordRequest): Observable<void> {
    return this.http.post<void>(`/api/accounts/${id}/password`, request);
  }
}
