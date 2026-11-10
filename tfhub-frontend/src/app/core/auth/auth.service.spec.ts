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
});
