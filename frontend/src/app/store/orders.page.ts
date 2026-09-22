import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { Order, Page } from '../core/models';

@Component({
  selector: 'app-orders-page',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <h1>My orders</h1>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (orders(); as page) {
      @for (o of page.content; track o.orderNumber) {
        <article class="card order-row">
          <div class="grow">
            <strong>{{ o.orderNumber }}</strong>
            <small class="muted">{{ o.createdAt | date: 'medium' }} · {{ o.units }} items</small>
            <small class="muted">
              @for (i of o.items; track i.sku; let last = $last) {
                {{ i.quantity }}× {{ i.productName }}{{ last ? '' : ', ' }}
              }
            </small>
          </div>
          <span class="status" [attr.data-status]="o.status">{{ o.status }}</span>
          <strong>{{ o.total | currency: 'USD' }}</strong>
        </article>
      } @empty {
        <p class="muted">You have no orders yet.</p>
      }
    }
  `,
})
export class OrdersPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly orders = signal<Page<Order> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.myOrders().subscribe({ next: (p) => this.orders.set(p), error: (e) => this.error.set(errorMessage(e)) });
  }
}
