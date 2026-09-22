import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/api.service';
import { Dashboard } from './admin.models';
import { AdminService } from './admin.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, DecimalPipe, DatePipe, RouterLink],
  template: `
    <div class="page-head">
      <h1>Dashboard</h1>
      <a class="btn primary" routerLink="/admin/ai">✨ Generate AI report</a>
    </div>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (data(); as d) {
      <div class="kpis">
        <div class="card kpi">
          <small>Revenue · 30 days</small>
          <strong>{{ d.sales.revenue30d | currency: 'USD' : 'symbol' : '1.0-0' }}</strong>
          <span [class]="d.sales.revenueGrowthPct >= 0 ? 'up' : 'down'">
            {{ d.sales.revenueGrowthPct >= 0 ? '▲' : '▼' }} {{ d.sales.revenueGrowthPct | number: '1.1-1' }}% vs previous 30 days
          </span>
        </div>
        <div class="card kpi">
          <small>Orders · 30 days</small>
          <strong>{{ d.sales.orders30d | number }}</strong>
          <span class="muted">{{ d.sales.pendingPaymentOrders }} awaiting payment</span>
        </div>
        <div class="card kpi">
          <small>Average order value</small>
          <strong>{{ d.sales.averageOrderValue | currency: 'USD' }}</strong>
          <span class="muted">per paid order</span>
        </div>
        <div class="card kpi">
          <small>Stock alerts</small>
          <strong>{{ d.inventory.stockouts + d.inventory.critical + d.inventory.low }}</strong>
          <span class="down">{{ d.inventory.stockouts }} out of stock · {{ d.inventory.critical }} critical</span>
        </div>
      </div>

      <div class="card chart-card">
        <div class="chart-head">
          <h3>Daily revenue</h3>
          <small class="muted">Last 30 days · paid orders</small>
        </div>
        <div class="bars" role="img" [attr.aria-label]="'Daily revenue, last 30 days, peak ' + (maxDaily() | currency: 'USD')">
          @for (day of d.dailySales; track day.date) {
            <div class="bar-slot" [attr.data-tip]="(day.date | date: 'EEE d MMM') + ' · ' + (day.revenue | currency: 'USD') + ' · ' + day.orders + ' orders'">
              <div class="bar" [style.height.%]="(day.revenue / maxDaily()) * 100"></div>
            </div>
          }
        </div>
        <div class="axis muted">
          <span>{{ d.dailySales[0].date | date: 'd MMM' }}</span>
          <span>{{ d.dailySales[d.dailySales.length - 1].date | date: 'd MMM' }}</span>
        </div>
      </div>

      <div class="two-col">
        <div class="card">
          <h3>Revenue by category</h3>
          @for (c of d.categories; track c.category) {
            <div class="hbar-row" [attr.data-tip]="c.units + ' units'">
              <span class="hbar-label">{{ c.category }}</span>
              <div class="hbar-track"><div class="hbar" [style.width.%]="(c.revenue / maxCategory()) * 100"></div></div>
              <span class="hbar-value">{{ c.revenue | currency: 'USD' : 'symbol' : '1.0-0' }}</span>
            </div>
          }
        </div>
        <div class="card">
          <h3>Best sellers</h3>
          <table class="table">
            <thead><tr><th>Product</th><th class="num">Units</th><th class="num">Revenue</th></tr></thead>
            <tbody>
              @for (p of d.topSellers; track p.productId) {
                <tr><td>{{ p.name }}</td><td class="num">{{ p.unitsSold30d }}</td><td class="num">{{ p.revenue30d | currency: 'USD' : 'symbol' : '1.0-0' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <h3>Restock now</h3>
        <p class="muted">Computed from sales velocity, a 7-day supplier lead time, 7 days of safety stock and a 30-day cover target.</p>
        <table class="table">
          <thead>
            <tr><th>SKU</th><th>Product</th><th>Status</th><th class="num">Available</th><th class="num">Sold/day</th><th class="num">Days of cover</th><th class="num">Suggested order</th></tr>
          </thead>
          <tbody>
            @for (p of d.restockNow; track p.productId) {
              <tr>
                <td><code>{{ p.sku }}</code></td>
                <td>{{ p.name }}</td>
                <td><span class="status" [attr.data-status]="p.health">{{ p.health }}</span></td>
                <td class="num">{{ p.available }}</td>
                <td class="num">{{ p.dailyVelocity | number: '1.1-2' }}</td>
                <td class="num">{{ p.daysOfCover ?? 0 }}</td>
                <td class="num"><strong>{{ p.suggestedReorderQty }}</strong></td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="muted">Nothing to restock.</td></tr>
            }
          </tbody>
        </table>
      </div>

      <div class="card">
        <h3>Slow-moving stock</h3>
        <table class="table">
          <thead><tr><th>SKU</th><th>Product</th><th>Status</th><th class="num">Available</th><th class="num">Tied-up value</th></tr></thead>
          <tbody>
            @for (p of d.slowMovers; track p.productId) {
              <tr>
                <td><code>{{ p.sku }}</code></td>
                <td>{{ p.name }}</td>
                <td><span class="status" [attr.data-status]="p.health">{{ p.health }}</span></td>
                <td class="num">{{ p.available }}</td>
                <td class="num">{{ p.available * p.price | currency: 'USD' : 'symbol' : '1.0-0' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (!error()) {
      <p class="muted">Loading…</p>
    }
  `,
})
export class DashboardPage implements OnInit {
  private readonly admin = inject(AdminService);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly maxDaily = computed(() => Math.max(1, ...(this.data()?.dailySales.map((d) => d.revenue) ?? [1])));
  protected readonly maxCategory = computed(() => Math.max(1, ...(this.data()?.categories.map((c) => c.revenue) ?? [1])));

  ngOnInit(): void {
    this.admin.dashboard().subscribe({ next: (d) => this.data.set(d), error: (e) => this.error.set(errorMessage(e)) });
  }
}
