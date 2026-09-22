import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { InstanceService } from './instance.service';

/** Adds the JWT to API calls and drops the session when the backend says it is no longer valid. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const instances = inject(InstanceService);
  const token = auth.token;
  const request = token && req.url.startsWith('/api/')
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        instances.record(event.headers.get('X-Served-By'));
      }
    }),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
