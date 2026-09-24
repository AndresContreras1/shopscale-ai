import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResult, Role, User } from './models';

/**
 * The session lives in a HttpOnly cookie that this code cannot read, which is the point: a script
 * injected into the page cannot steal it either. Who the user is comes from the API, never from
 * browser storage, so a tampered entry in localStorage cannot grant a role.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly current = signal<User | null>(null);
  private readonly resolved = signal(false);

  readonly user = computed(() => this.current());
  readonly isLoggedIn = computed(() => this.current() !== null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly isStaff = computed(() => this.user()?.role === 'ADMIN' || this.user()?.role === 'OPERATOR');
  /** False until the first call to /me answers, so a guard does not redirect during the page load. */
  readonly sessionChecked = computed(() => this.resolved());

  constructor() {
    this.restore();
  }

  login(email: string, password: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>('/api/auth/login', { email, password })
      .pipe(tap((result) => this.current.set(result.user)));
  }

  /** Second step for a staff account: the code from the authenticator app, or a recovery code. */
  verifySecondFactor(code: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>('/api/auth/mfa/verify', { code })
      .pipe(tap((result) => this.current.set(result.user)));
  }

  register(email: string, password: string, fullName: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>('/api/auth/register', { email, password, fullName })
      .pipe(tap((result) => this.current.set(result.user)));
  }

  logout(redirect = true): void {
    this.http.post<void>('/api/auth/logout', {}).subscribe({
      next: () => this.finish(redirect),
      error: () => this.finish(redirect),
    });
  }

  /** Drops the local view of the session without calling the API, for when the API already said 401. */
  clearSession(): void {
    this.current.set(null);
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.user()?.role;
    return role !== undefined && roles.includes(role);
  }

  private finish(redirect: boolean): void {
    this.current.set(null);
    if (redirect) {
      this.router.navigateByUrl('/');
    }
  }

  private restore(): void {
    this.http.get<User>('/api/auth/me').subscribe({
      next: (user) => {
        this.current.set(user);
        this.resolved.set(true);
      },
      error: () => {
        this.current.set(null);
        this.resolved.set(true);
      },
    });
  }
}
