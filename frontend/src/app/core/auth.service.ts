import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Role, User } from './models';

const STORAGE_KEY = 'shopscale.session';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly isStaff = computed(() => this.user()?.role === 'ADMIN' || this.user()?.role === 'OPERATOR');

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap((r) => this.store(r)));
  }

  register(email: string, password: string, fullName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { email, password, fullName })
      .pipe(tap((r) => this.store(r)));
  }

  logout(redirect = true): void {
    this.session.set(null);
    localStorage.removeItem(STORAGE_KEY);
    if (redirect) {
      this.router.navigateByUrl('/');
    }
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.user()?.role;
    return role !== undefined && roles.includes(role);
  }

  private store(response: AuthResponse): void {
    const session: Session = { token: response.token, expiresAt: response.expiresAt, user: response.user };
    this.session.set(session);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      return new Date(session.expiresAt) > new Date() ? session : null;
    } catch {
      return null;
    }
  }
}
