import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  template: `
    <section class="auth-wrap">
      @if (awaitingCode()) {
        <form class="card auth-card" (ngSubmit)="submitCode()">
          <h1>One more step</h1>
          <p class="muted">Enter the six digit code from your authenticator app, or a recovery code.</p>
          <label>Code <input class="input" name="code" [(ngModel)]="code" required autocomplete="one-time-code" /></label>
          @if (error()) {
            <div class="alert error">{{ error() }}</div>
          }
          <button class="btn primary block" type="submit" [disabled]="busy()">Verify</button>
        </form>
      } @else {
        <form class="card auth-card" (ngSubmit)="submit()">
          <h1>{{ registering() ? 'Create account' : 'Sign in' }}</h1>
          @if (registering()) {
            <label>Full name <input class="input" name="fullName" [(ngModel)]="fullName" required /></label>
          }
          <label>Email <input class="input" name="email" type="email" [(ngModel)]="email" required /></label>
          <label>Password <input class="input" name="password" type="password" [(ngModel)]="password" required /></label>
          @if (error()) {
            <div class="alert error">{{ error() }}</div>
          }
          <button class="btn primary block" type="submit" [disabled]="busy()">
            {{ registering() ? 'Create account' : 'Sign in' }}
          </button>
          <button class="link" type="button" (click)="registering.set(!registering())">
            {{ registering() ? 'I already have an account' : 'New here? Create an account' }}
          </button>
        </form>
      }

      <aside class="card demo-accounts">
        <h3>Demo accounts</h3>
        <p class="muted">One click to sign in with each role.</p>
        @for (a of demoAccounts; track a.email) {
          <button class="btn block" (click)="quickLogin(a.email, a.password)">
            <strong>{{ a.role }}</strong> · {{ a.email }}
          </button>
        }
      </aside>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected email = '';
  protected password = '';
  protected fullName = '';
  protected code = '';
  protected readonly registering = signal(false);
  protected readonly awaitingCode = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly demoAccounts = [
    { role: 'ADMIN', email: 'admin@gamestore.co', password: 'Demo-Admin-2026!' },
    { role: 'OPERATOR', email: 'operator@gamestore.co', password: 'Demo-Operator-2026!' },
    { role: 'CUSTOMER', email: 'customer@gamestore.co', password: 'Demo-Customer-2026!' },
  ];

  quickLogin(email: string, password: string): void {
    this.email = email;
    this.password = password;
    this.registering.set(false);
    this.submit();
  }

  /** Second step for staff accounts: the code from the authenticator app, or a recovery code. */
  submitCode(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.verifySecondFactor(this.code).subscribe({
      next: (result) => this.goHome(result.user?.role),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }

  private goHome(role: string | undefined): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    this.router.navigateByUrl(returnUrl ?? (role === 'CUSTOMER' ? '/' : '/admin'));
  }

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    const call = this.registering()
      ? this.auth.register(this.email, this.password, this.fullName)
      : this.auth.login(this.email, this.password);
    call.subscribe({
      next: (result) => {
        if (result.mfaRequired) {
          this.awaitingCode.set(true);
          this.busy.set(false);
          return;
        }
        this.goHome(result.user?.role);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
