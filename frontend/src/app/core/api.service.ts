import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiError, Category, Order, Page, Product } from './models';

export interface ProductQuery {
  q?: string;
  categoryId?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  status?: string | null;
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

/** Storefront endpoints: catalog and orders. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  products(query: ProductQuery): Observable<Page<Product>> {
    return this.http.get<Page<Product>>('/api/products', { params: toParams(query) });
  }

  product(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  checkout(items: { productId: number; quantity: number }[]): Observable<Order> {
    return this.http.post<Order>('/api/orders', { items });
  }

  pay(orderNumber: string): Observable<Order> {
    return this.http.post<Order>(`/api/orders/${orderNumber}/pay`, {});
  }

  cancel(orderNumber: string): Observable<Order> {
    return this.http.post<Order>(`/api/orders/${orderNumber}/cancel`, {});
  }

  myOrders(page = 0): Observable<Page<Order>> {
    return this.http.get<Page<Order>>('/api/orders/mine', { params: { page, size: 10 } });
  }
}

export function toParams(query: object): HttpParams {
  let params = new HttpParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  });
  return params;
}

/** Human-readable message from any backend error. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiError | null;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length) {
      return Object.entries(body.fieldErrors).map(([f, m]) => `${f}: ${m}`).join(' · ');
    }
    if (body?.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'Cannot reach the API. Is the backend running?';
    }
    return `${error.status} ${error.statusText}`;
  }
  return 'Unexpected error';
}
