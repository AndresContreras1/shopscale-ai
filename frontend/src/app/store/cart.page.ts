import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CartService } from '../core/cart.service';
import { Order } from '../core/models';
import { visualFor } from '../core/visuals';

@Component({
  selector: 'app-cart-page',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <h1>Your cart</h1>

    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }

    @if (order(); as o) {
      <section class="card order-box">
        @switch (o.status) {
          @case ('PENDING_PAYMENT') {
            <h2>Order {{ o.orderNumber }} created</h2>
            <p>
              Your items are <strong>reserved</strong> for <strong>{{ remaining() }}</strong>.
              If you do not pay in time the reservation is released automatically.
            </p>
            <p class="total">Total: {{ o.total | currency: 'USD' }}</p>
            <div class="actions">
              <button class="btn primary" [disabled]="busy()" (click)="pay(o)">Pay now (simulated)</button>
              <button class="btn" [disabled]="busy()" (click)="cancel(o)">Cancel order</button>
            </div>
          }
          @case ('PAID') {
            <h2>✓ Payment approved</h2>
            <p>Order {{ o.orderNumber }} paid on {{ o.paidAt | date: 'medium' }}. The stock has been committed.</p>
            <a class="btn primary" routerLink="/orders">View my orders</a>
          }
          @default {
            <h2>Order {{ o.orderNumber }}: {{ o.status }}</h2>
            <p>The reserved units are available again.</p>
            <a class="btn" routerLink="/">Back to the store</a>
          }
        }
      </section>
    } @else if (cart.lines().length === 0) {
      <div class="card empty">
        <p>Your cart is empty.</p>
        <a class="btn primary" routerLink="/">Browse products</a>
      </div>
    } @else {
      <div class="cart-layout">
        <section class="card">
          @for (line of cart.lines(); track line.product.id) {
            <div class="cart-line">
              <div class="thumb" [style.background]="visual(line.product.categoryName).background">
                {{ visual(line.product.categoryName).icon }}
              </div>
              <div class="grow">
                <strong>{{ line.product.name }}</strong>
                <small class="muted">{{ line.product.sku }} · {{ line.product.price | currency: 'USD' }}</small>
              </div>
              <div class="stepper">
                <button (click)="cart.setQuantity(line.product.id, line.quantity - 1)">−</button>
                <span>{{ line.quantity }}</span>
                <button (click)="cart.setQuantity(line.product.id, line.quantity + 1)">+</button>
              </div>
              <strong class="line-total">{{ line.product.price * line.quantity | currency: 'USD' }}</strong>
              <button class="icon-btn" title="Remove" (click)="cart.remove(line.product.id)">✕</button>
            </div>
          }
        </section>
        <aside class="card summary">
          <h3>Summary</h3>
          <div class="row"><span>Items</span><span>{{ cart.count() }}</span></div>
          <div class="row"><span>Shipping</span><span>Free</span></div>
          <div class="row total"><span>Total</span><span>{{ cart.total() | currency: 'USD' }}</span></div>
          <button class="btn primary block" [disabled]="busy()" (click)="checkout()">
            {{ auth.isLoggedIn() ? 'Checkout' : 'Sign in to checkout' }}
          </button>
          <small class="muted">Stock is reserved atomically: two buyers can never get the last unit.</small>
        </aside>
      </div>
    }
  `,
})
export class CartPage implements OnDestroy {
  protected readonly cart = inject(CartService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly order = signal<Order | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  private readonly now = signal(Date.now());
  private readonly timer = setInterval(() => this.now.set(Date.now()), 1000);

  protected readonly remaining = computed(() => {
    const expires = this.order()?.expiresAt;
    if (!expires) {
      return '';
    }
    const ms = Math.max(0, new Date(expires).getTime() - this.now());
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  });

  protected visual = visualFor;

  checkout(): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/cart' } });
      return;
    }
    this.run(this.api.checkout(this.cart.lines().map((l) => ({ productId: l.product.id, quantity: l.quantity }))), () =>
      this.cart.clear(),
    );
  }

  pay(order: Order): void {
    this.run(this.api.pay(order.orderNumber));
  }

  cancel(order: Order): void {
    this.run(this.api.cancel(order.orderNumber));
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  private run(call: ReturnType<ApiService['pay']>, onSuccess?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (o) => {
        this.order.set(o);
        this.busy.set(false);
        onSuccess?.();
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
