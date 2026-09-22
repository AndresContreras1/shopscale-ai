import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Product } from '../core/models';
import { AiReport, AiStatus, AuditEntry, Dashboard, FlashSaleResult, InventoryRow, Movement } from './admin.models';

export interface ProductForm {
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: number | null;
  price: number | null;
  compareAtPrice: number | null;
  status: string;
  initialStock: number | null;
  reorderPoint: number | null;
}

/** Back-office endpoints: dashboard, inventory, products, AI and audit. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/admin/dashboard');
  }

  inventory(lowStock: boolean, page: number): Observable<Page<InventoryRow>> {
    return this.http.get<Page<InventoryRow>>('/api/inventory', { params: { lowStock, page, size: 15 } });
  }

  movements(productId: number): Observable<Page<Movement>> {
    return this.http.get<Page<Movement>>(`/api/inventory/${productId}/movements`, { params: { size: 15 } });
  }

  receive(productId: number, quantity: number, reason: string): Observable<InventoryRow> {
    return this.http.post<InventoryRow>(`/api/inventory/${productId}/receipts`, { quantity, reason });
  }

  adjust(productId: number, quantity: number, reason: string): Observable<InventoryRow> {
    return this.http.post<InventoryRow>(`/api/inventory/${productId}/adjustments`, { quantity, reason });
  }

  flashSale(productId: number, buyers: number, unitsPerBuyer: number): Observable<FlashSaleResult> {
    return this.http.post<FlashSaleResult>('/api/inventory/simulations/flash-sale', { productId, buyers, unitsPerBuyer });
  }

  aiStatus(): Observable<AiStatus> {
    return this.http.get<AiStatus>('/api/ai/status');
  }

  aiReport(type: 'inventory' | 'sales', language: string): Observable<AiReport> {
    return this.http.post<AiReport>(`/api/ai/reports/${type}`, { language });
  }

  aiDescription(productId: number, language: string): Observable<AiReport> {
    return this.http.post<AiReport>(`/api/ai/products/${productId}/description`, { language });
  }

  createProduct(form: ProductForm): Observable<Product> {
    return this.http.post<Product>('/api/products', form);
  }

  updateProduct(id: number, form: ProductForm): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, form);
  }

  audit(page = 0): Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>('/api/audit', { params: { page, size: 30 } });
  }
}
