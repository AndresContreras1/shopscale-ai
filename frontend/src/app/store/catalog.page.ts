import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService, errorMessage } from '../core/api.service';
import { CartService } from '../core/cart.service';
import { Category, Page, Product } from '../core/models';
import { ProductCard } from './product-card';

@Component({
  selector: 'app-catalog-page',
  imports: [FormsModule, ProductCard],
  template: `
    <section class="hero">
      <div>
        <h1>Everything you need, delivered fast.</h1>
        <p>
          Demo storefront running on a scalable Spring Boot API: real-time stock, safe checkout under load
          and AI-generated business reports.
        </p>
      </div>
    </section>

    <section class="toolbar">
      <input class="input search" type="search" placeholder="Search by name, brand or SKU…"
             [ngModel]="query()" (ngModelChange)="search$.next($event)" />
      <select class="input" [ngModel]="sort()" (ngModelChange)="changeSort($event)">
        <option value="name:asc">Name A-Z</option>
        <option value="price:asc">Price: low to high</option>
        <option value="price:desc">Price: high to low</option>
        <option value="createdAt:desc">Newest</option>
      </select>
    </section>

    <nav class="chips">
      <button class="chip" [class.active]="categoryId() === null" (click)="selectCategory(null)">All</button>
      @for (c of categories(); track c.id) {
        <button class="chip" [class.active]="categoryId() === c.id" (click)="selectCategory(c.id)">{{ c.name }}</button>
      }
    </nav>

    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (added()) {
      <div class="toast">✓ {{ added() }} added to cart</div>
    }

    @if (page(); as p) {
      <p class="muted">{{ p.totalElements }} products</p>
      <div class="grid products">
        @for (product of p.content; track product.id) {
          <app-product-card [product]="product" (add)="addToCart($event)" />
        } @empty {
          <p class="muted">No products match your search.</p>
        }
      </div>
      @if (p.totalPages > 1) {
        <div class="pager">
          <button class="btn" [disabled]="p.page === 0" (click)="goTo(p.page - 1)">← Previous</button>
          <span>Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
          <button class="btn" [disabled]="p.page + 1 >= p.totalPages" (click)="goTo(p.page + 1)">Next →</button>
        </div>
      }
    } @else if (!error()) {
      <p class="muted">Loading catalog…</p>
    }
  `,
})
export class CatalogPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly page = signal<Page<Product> | null>(null);
  protected readonly query = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly sort = signal('name:asc');
  protected readonly error = signal<string | null>(null);
  protected readonly added = signal<string | null>(null);
  protected readonly search$ = new Subject<string>();
  private pageIndex = 0;

  constructor() {
    this.search$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((q) => {
      this.query.set(q);
      this.goTo(0);
    });
  }

  ngOnInit(): void {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c), error: (e) => this.error.set(errorMessage(e)) });
    this.load();
  }

  selectCategory(id: number | null): void {
    this.categoryId.set(id);
    this.goTo(0);
  }

  changeSort(value: string): void {
    this.sort.set(value);
    this.goTo(0);
  }

  goTo(index: number): void {
    this.pageIndex = index;
    this.load();
  }

  addToCart(product: Product): void {
    this.cart.add(product);
    this.added.set(product.name);
    setTimeout(() => this.added.set(null), 1800);
  }

  private load(): void {
    const [sort, direction] = this.sort().split(':') as [string, 'asc' | 'desc'];
    this.api
      .products({ q: this.query(), categoryId: this.categoryId(), status: 'ACTIVE', page: this.pageIndex, size: 12, sort, direction })
      .subscribe({
        next: (p) => {
          this.page.set(p);
          this.error.set(null);
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }
}
