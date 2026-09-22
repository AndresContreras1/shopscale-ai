import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../core/api.service';
import { Page } from '../core/models';
import { FlashSaleResult, InventoryRow, Movement } from './admin.models';
import { AdminService } from './admin.service';

@Component({
  selector: 'app-inventory-page',
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page-head">
      <h1>Inventory</h1>
      <label class="toggle">
        <input type="checkbox" [ngModel]="lowOnly()" (ngModelChange)="lowOnly.set($event); load(0)" />
        Only low stock
      </label>
    </div>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (notice()) {
      <div class="alert success">{{ notice() }}</div>
    }

    <div class="card flash">
      <div>
        <h3>⚡ Flash sale simulator</h3>
        <p class="muted">
          Fires concurrent buyers at the selected product at the same instant. Reservations use an atomic
          conditional UPDATE, so stock can never go negative. The simulation releases its reservations at the end.
        </p>
        <div class="flash-form">
          <label>Buyers <input class="input" type="number" min="1" max="500" [(ngModel)]="buyers" /></label>
          <label>Units each <input class="input" type="number" min="1" max="10" [(ngModel)]="unitsEach" /></label>
          <button class="btn primary" [disabled]="!selected() || busy()" (click)="flashSale()">
            {{ selected() ? 'Run on ' + selected()!.sku : 'Select a product below' }}
          </button>
        </div>
      </div>
      @if (flash(); as f) {
        <div class="flash-result">
          <div><small>Available before</small><strong>{{ f.availableBefore }}</strong></div>
          <div><small>Buyers served</small><strong class="up">{{ f.successfulReservations }}</strong></div>
          <div><small>Rejected (no stock)</small><strong class="down">{{ f.rejectedNoStock }}</strong></div>
          <div><small>Lowest available</small><strong>{{ f.minAvailableObserved }}</strong></div>
          <div><small>Oversold?</small><strong [class]="f.oversold ? 'down' : 'up'">{{ f.oversold ? 'YES' : 'NO' }}</strong></div>
          <div><small>Time</small><strong>{{ f.elapsedMs }} ms</strong></div>
        </div>
      }
    </div>

    @if (page(); as p) {
      <div class="card">
        <table class="table">
          <thead>
            <tr><th>SKU</th><th>Product</th><th class="num">On hand</th><th class="num">Reserved</th><th class="num">Available</th><th class="num">Reorder at</th><th></th></tr>
          </thead>
          <tbody>
            @for (row of p.content; track row.productId) {
              <tr [class.selected]="selected()?.productId === row.productId" (click)="select(row)">
                <td><code>{{ row.sku }}</code></td>
                <td>{{ row.productName }}</td>
                <td class="num">{{ row.onHand }}</td>
                <td class="num">{{ row.reserved }}</td>
                <td class="num"><strong [class.down]="row.lowStock">{{ row.available }}</strong></td>
                <td class="num">{{ row.reorderPoint }}</td>
                <td class="actions">
                  <button class="btn small" (click)="receive(row); $event.stopPropagation()">+ Receive</button>
                  <button class="btn small" (click)="adjust(row); $event.stopPropagation()">Adjust</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <div class="pager">
          <button class="btn small" [disabled]="p.page === 0" (click)="load(p.page - 1)">←</button>
          <span>{{ p.page + 1 }} / {{ p.totalPages }}</span>
          <button class="btn small" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">→</button>
        </div>
      </div>
    }

    @if (selected(); as s) {
      <div class="card">
        <h3>Stock ledger · {{ s.sku }}</h3>
        <p class="muted">Append-only history: every unit that entered, was reserved or left the warehouse.</p>
        <table class="table">
          <thead><tr><th>When</th><th>Type</th><th class="num">Qty</th><th class="num">On hand after</th><th class="num">Reserved after</th><th>Reference</th><th>By</th></tr></thead>
          <tbody>
            @for (m of movements(); track m.id) {
              <tr>
                <td>{{ m.createdAt | date: 'short' }}</td>
                <td><span class="status">{{ m.type }}</span></td>
                <td class="num">{{ m.quantity > 0 ? '+' : '' }}{{ m.quantity }}</td>
                <td class="num">{{ m.onHandAfter }}</td>
                <td class="num">{{ m.reservedAfter }}</td>
                <td>{{ m.reference ?? '' }}</td>
                <td>{{ m.createdBy }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class InventoryPage implements OnInit {
  private readonly admin = inject(AdminService);

  protected readonly page = signal<Page<InventoryRow> | null>(null);
  protected readonly lowOnly = signal(false);
  protected readonly selected = signal<InventoryRow | null>(null);
  protected readonly movements = signal<Movement[]>([]);
  protected readonly flash = signal<FlashSaleResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected buyers = 200;
  protected unitsEach = 1;

  ngOnInit(): void {
    this.load(0);
  }

  load(index: number): void {
    this.admin.inventory(this.lowOnly(), index).subscribe({
      next: (p) => this.page.set(p),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  select(row: InventoryRow): void {
    this.selected.set(row);
    this.flash.set(null);
    this.admin.movements(row.productId).subscribe({ next: (p) => this.movements.set(p.content) });
  }

  receive(row: InventoryRow): void {
    const qty = Number(prompt(`Units received for ${row.sku}:`, '50'));
    if (qty > 0) {
      this.change(this.admin.receive(row.productId, qty, 'Supplier delivery'), row, `Received ${qty} units of ${row.sku}`);
    }
  }

  adjust(row: InventoryRow): void {
    const qty = Number(prompt(`Adjustment for ${row.sku} (negative removes units):`, '-1'));
    const reason = qty ? prompt('Reason:', 'Physical count correction') : null;
    if (qty && reason) {
      this.change(this.admin.adjust(row.productId, qty, reason), row, `Adjusted ${row.sku} by ${qty}`);
    }
  }

  flashSale(): void {
    const s = this.selected();
    if (!s) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.admin.flashSale(s.productId, this.buyers, this.unitsEach).subscribe({
      next: (r) => {
        this.flash.set(r);
        this.busy.set(false);
        this.select(s);
        this.flash.set(r);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }

  private change(call: ReturnType<AdminService['receive']>, row: InventoryRow, message: string): void {
    this.error.set(null);
    call.subscribe({
      next: () => {
        this.notice.set(message);
        setTimeout(() => this.notice.set(null), 2500);
        this.load(this.page()?.page ?? 0);
        this.select(row);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
