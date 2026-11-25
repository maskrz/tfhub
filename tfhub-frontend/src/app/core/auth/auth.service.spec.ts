import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('posts the current and new passwords to the change-password endpoint', () => {
    let completed = false;
    service.changePassword('current-secret', 'new-secret').subscribe(() => {
      completed = true;
    });

    const request = httpTesting.expectOne('/api/auth/change-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      currentPassword: 'current-secret',
      newPassword: 'new-secret',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
    expect(service.currentUser()).toBeNull();
  });

  it('lists sessions and revokes an opaque session handle', () => {
    const sessions = [{
      id: 'opaque-session-handle',
      createdAt: '2026-11-24T10:00:00Z',
      lastActivityAt: '2026-11-24T11:00:00Z',
      current: true,
    }];
    service.getSessions().subscribe((result) => expect(result).toEqual(sessions));
    const listRequest = httpTesting.expectOne('/api/auth/sessions');
    expect(listRequest.request.method).toBe('GET');
    listRequest.flush(sessions);

    service.revokeSession('opaque/session-handle').subscribe();
    const revokeRequest = httpTesting.expectOne('/api/auth/sessions/opaque%2Fsession-handle');
    expect(revokeRequest.request.method).toBe('DELETE');
    revokeRequest.flush(null, { status: 204, statusText: 'No Content' });
  });
});
