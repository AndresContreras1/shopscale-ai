import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { InstanceService } from './instance.service';

/**
 * Nothing is added to the request: the session cookie travels on its own and Angular attaches the
 * XSRF header by itself. This only reacts when the API says the session is no longer valid.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const instances = inject(InstanceService);

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        instances.record(event.headers.get('X-Served-By'));
      }
    }),
    catchError((error: HttpErrorResponse) => {
      // Ignoring /me avoids reacting while the app is still asking whether anyone is signed in.
      if (error.status === 401 && auth.isLoggedIn() && !req.url.endsWith('/api/auth/me')) {
        auth.clearSession();
      }
      return throwError(() => error);
    }),
  );
};
