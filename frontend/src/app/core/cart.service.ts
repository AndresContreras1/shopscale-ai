import { Injectable, computed, effect, signal } from '@angular/core';
import { CartLine, Product } from './models';

const STORAGE_KEY = 'shopscale.cart';

@Injectable({ providedIn: 'root' })
export class CartService {
  readonly lines = signal<CartLine[]>(this.restore());
  readonly count = computed(() => this.lines().reduce((n, l) => n + l.quantity, 0));
  readonly total = computed(() => this.lines().reduce((sum, l) => sum + l.product.price * l.quantity, 0));

  constructor() {
    effect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(this.lines())));
  }

  add(product: Product, quantity = 1): void {
    this.lines.update((lines) => {
      const existing = lines.find((l) => l.product.id === product.id);
      if (existing) {
        return lines.map((l) => (l.product.id === product.id ? { ...l, quantity: l.quantity + quantity } : l));
      }
      return [...lines, { product, quantity }];
    });
  }

  setQuantity(productId: number, quantity: number): void {
    if (quantity <= 0) {
      this.remove(productId);
      return;
    }
    this.lines.update((lines) => lines.map((l) => (l.product.id === productId ? { ...l, quantity } : l)));
  }

  remove(productId: number): void {
    this.lines.update((lines) => lines.filter((l) => l.product.id !== productId));
  }

  clear(): void {
    this.lines.set([]);
  }

  private restore(): CartLine[] {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as CartLine[];
    } catch {
      return [];
    }
  }
}
