import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((user) => {
      if (!user) {
        return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
      }
      const path = state.url.split(/[?#]/, 1)[0];
      if (user.role === 'ATHLETE' && path !== `/athletes/${user.athleteId}`) {
        return router.createUrlTree(['/athletes', user.athleteId]);
      }
      return true;
    }),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

export const homeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((user) => {
      if (!user) return router.createUrlTree(['/login']);
      return user.role === 'ATHLETE' && user.athleteId !== null
        ? router.createUrlTree(['/athletes', user.athleteId])
        : router.createUrlTree(['/dashboard']);
    }),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((user) => {
      if (!user) return router.createUrlTree(['/login']);
      return user.role === 'ADMIN' || user.role === 'COACH'
        ? true
        : router.createUrlTree(['/athletes', user.athleteId]);
    }),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((user) => {
      if (!user) return router.createUrlTree(['/login']);
      return user.role === 'ADMIN'
        ? true
        : user.role === 'ATHLETE'
          ? router.createUrlTree(['/athletes', user.athleteId])
          : router.createUrlTree(['/dashboard']);
    }),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
