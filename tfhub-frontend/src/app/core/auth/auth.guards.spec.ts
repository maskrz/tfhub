import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { routes } from '../../app.routes';
import { AuthService } from './auth.service';
import { adminGuard } from './auth.guards';

describe('adminGuard', () => {
  let auth: { ensureSession: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { ensureSession: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  const activate = () => firstValueFrom(TestBed.runInInjectionContext(() =>
    adminGuard({} as never, { url: '/accounts' } as never),
  ) as import('rxjs').Observable<boolean | ReturnType<Router['createUrlTree']>>);

  it('protects the accounts route and redirects authenticated non-admins', async () => {
    const accountsRoute = routes.find((route) => route.path === 'accounts');
    expect(accountsRoute?.canActivate).toContain(adminGuard);

    auth.ensureSession.mockReturnValue(of({
      authenticated: true, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null,
    }));
    expect(await activate()).toBe(true);

    auth.ensureSession.mockReturnValue(of({
      authenticated: true, userId: 2, username: 'coach', role: 'COACH', athleteId: null,
    }));
    const result = await activate();
    expect(result).not.toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/dashboard');
  });

  it('redirects unauthenticated direct route access to login', async () => {
    auth.ensureSession.mockReturnValue(of(null));
    const result = await activate();
    expect(result).not.toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login');
  });
});
