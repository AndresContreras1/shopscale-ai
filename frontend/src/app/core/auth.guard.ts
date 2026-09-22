import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Allows the route only to logged-in users with one of the given roles (any role when empty). */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    return roles.length === 0 || auth.hasRole(...roles) ? true : router.createUrlTree(['/']);
  };
}
