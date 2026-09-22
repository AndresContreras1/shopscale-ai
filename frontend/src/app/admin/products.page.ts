import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { Category, Page, Product } from '../core/models';
import { AdminService, ProductForm } from './admin.service';

const EMPTY: ProductForm = {
  sku: '',
  name: '',
  description: '',
  brand: '',
  categoryId: null,
  price: null,
  compareAtPrice: null,
  status: 'ACTIVE',
  initialStock: 0,
  reorderPoint: 10,
};

@Component({
  selector: 'app-products-page',
  imports: [FormsModule, CurrencyPipe],
  template: `
    <div class="page-head">
      <h1>Products</h1>
      <button class="btn primary" (click)="startCreate()">+ New product</button>
    </div>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (notice()) {
      <div class="alert success">{{ notice() }}</div>
    }

    @if (form(); as f) {
      <form class="card product-form" (ngSubmit)="save()">
        <h3>{{ editingId() ? 'Edit product' : 'New product' }}</h3>
        <div class="form-grid">
          <label>SKU <input class="input" name="sku" [(ngModel)]="f.sku" placeholder="ELEC-NEW-001" /></label>
          <label>Name <input class="input" name="name" [(ngModel)]="f.name" /></label>
          <label>Brand <input class="input" name="brand" [(ngModel)]="f.brand" /></label>
          <label>Category
            <select class="input" name="categoryId" [(ngModel)]="f.categoryId">
              @for (c of categories(); track c.id) {
                <option [ngValue]="c.id">{{ c.name }}</option>
              }
            </select>
          </label>
          <label>Price <input class="input" name="price" type="number" step="0.01" [(ngModel)]="f.price" /></label>
          <label>Compare-at price <input class="input" name="compareAtPrice" type="number" step="0.01" [(ngModel)]="f.compareAtPrice" /></label>
          @if (!editingId()) {
            <label>Initial stock <input class="input" name="initialStock" type="number" [(ngModel)]="f.initialStock" /></label>
            <label>Reorder point <input class="input" name="reorderPoint" type="number" [(ngModel)]="f.reorderPoint" /></label>
          }
          <label>Status
            <select class="input" name="status" [(ngModel)]="f.status">
              <option>ACTIVE</option><option>DRAFT</option><option>ARCHIVED</option>
            </select>
          </label>
        </div>
        <label>Description
          <textarea class="input" name="description" rows="5" [(ngModel)]="f.description"></textarea>
        </label>
        <div class="actions">
          @if (editingId()) {
            <button class="btn" type="button" [disabled]="busy()" (click)="suggestDescription()">✨ Suggest description with AI</button>
          }
          <span class="grow"></span>
          <button class="btn" type="button" (click)="form.set(null)">Cancel</button>
          <button class="btn primary" type="submit" [disabled]="busy()">Save</button>
        </div>
        <small class="muted">Price changes are recorded in the audit log.</small>
      </form>
    }

    <input class="input search" type="search" placeholder="Search products…" [ngModel]="query" (ngModelChange)="query = $event; load(0)" />
    @if (page(); as p) {
      <div class="card">
        <table class="table">
          <thead><tr><th>SKU</th><th>Name</th><th>Category</th><th class="num">Price</th><th class="num">Stock</th><th>Status</th><th></th></tr></thead>
          <tbody>
            @for (prod of p.content; track prod.id) {
              <tr>
                <td><code>{{ prod.sku }}</code></td>
                <td>{{ prod.name }}</td>
                <td>{{ prod.categoryName }}</td>
                <td class="num">{{ prod.price | currency: 'USD' }}</td>
                <td class="num">{{ prod.availableStock }}</td>
                <td><span class="status" [attr.data-status]="prod.status === 'ACTIVE' ? 'PAID' : 'CANCELLED'">{{ prod.status }}</span></td>
                <td><button class="btn small" (click)="startEdit(prod)">Edit</button></td>
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
  `,
})
export class ProductsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly admin = inject(AdminService);

  protected readonly page = signal<Page<Product> | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly form = signal<ProductForm | null>(null);
  protected readonly editingId = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected query = '';

  ngOnInit(): void {
    this.api.categories().subscribe((c) => this.categories.set(c));
    this.load(0);
  }

  load(index: number): void {
    this.api.products({ q: this.query, page: index, size: 15, sort: 'sku' }).subscribe({
      next: (p) => this.page.set(p),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.form.set({ ...EMPTY, categoryId: this.categories()[0]?.id ?? null });
  }

  startEdit(p: Product): void {
    this.editingId.set(p.id);
    this.form.set({
      sku: p.sku,
      name: p.name,
      description: p.description ?? '',
      brand: p.brand ?? '',
      categoryId: p.categoryId,
      price: p.price,
      compareAtPrice: p.compareAtPrice,
      status: p.status,
      initialStock: null,
      reorderPoint: null,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  suggestDescription(): void {
    const id = this.editingId();
    const f = this.form();
    if (!id || !f) {
      return;
    }
    this.busy.set(true);
    this.admin.aiDescription(id, 'en').subscribe({
      next: (r) => {
        this.form.set({ ...f, description: r.markdown });
        this.notice.set(`Suggested by ${r.provider}. Review it before saving.`);
        this.busy.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }

  save(): void {
    const f = this.form();
    if (!f) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const id = this.editingId();
    const call = id ? this.admin.updateProduct(id, f) : this.admin.createProduct(f);
    call.subscribe({
      next: (p) => {
        this.notice.set(`${p.sku} saved`);
        this.form.set(null);
        this.busy.set(false);
        this.load(this.page()?.page ?? 0);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
