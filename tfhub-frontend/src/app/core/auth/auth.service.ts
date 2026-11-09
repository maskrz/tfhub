import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { AuthenticatedUser } from './auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<AuthenticatedUser | null>(null);

  private readonly http = inject(HttpClient);

  ensureSession(): Observable<AuthenticatedUser | null> {
    const user = this.currentUser();
    if (user) return of(user);
    return this.http.get<AuthenticatedUser>('/api/auth/session').pipe(
      tap((authenticatedUser) => this.currentUser.set(authenticatedUser)),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.currentUser.set(null);
          return of(null);
        }
        return throwError(() => error);
      }),
    );
  }

  login(username: string, password: string): Observable<AuthenticatedUser> {
    return this.http.post<AuthenticatedUser>('/api/auth/login', { username, password }).pipe(
      tap((user) => this.currentUser.set(user)),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      switchMap(() => this.refreshCsrfToken()),
      tap(() => this.currentUser.set(null)),
      catchError((error: unknown) => {
        this.currentUser.set(null);
        return throwError(() => error);
      }),
    );
  }

  clearSession(): void {
    this.currentUser.set(null);
  }

  refreshCsrfToken(): Observable<void> {
    return this.http.get<void>('/api/csrf').pipe(map(() => undefined));
  }
}
