import { CurrencyPipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { Product } from '../core/models';
import { visualFor } from '../core/visuals';

@Component({
  selector: 'app-product-card',
  imports: [CurrencyPipe],
  template: `
    <article class="card product">
      <div class="product-visual" [style.background]="visual().background">
        <span>{{ visual().icon }}</span>
        @if (product().compareAtPrice) {
          <span class="badge sale">-{{ discount() }}%</span>
        }
      </div>
      <div class="product-body">
        <small class="muted">{{ product().brand }} · {{ product().categoryName }}</small>
        <h3>{{ product().name }}</h3>
        <div class="price-row">
          <strong>{{ product().price | currency: 'USD' }}</strong>
          @if (product().compareAtPrice; as old) {
            <s class="muted">{{ old | currency: 'USD' }}</s>
          }
        </div>
        @if (stock() <= 0) {
          <span class="stock out">Out of stock</span>
        } @else if (stock() <= 5) {
          <span class="stock low">Only {{ stock() }} left</span>
        } @else {
          <span class="stock ok">In stock</span>
        }
        <button class="btn primary block" [disabled]="stock() <= 0" (click)="add.emit(product())">
          Add to cart
        </button>
      </div>
    </article>
  `,
})
export class ProductCard {
  readonly product = input.required<Product>();
  readonly add = output<Product>();

  protected readonly visual = computed(() => visualFor(this.product().categoryName));
  protected readonly stock = computed(() => this.product().availableStock ?? 0);
  protected readonly discount = computed(() => {
    const p = this.product();
    return p.compareAtPrice ? Math.round((1 - p.price / p.compareAtPrice) * 100) : 0;
  });
}
