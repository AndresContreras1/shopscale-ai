export type Role = 'ADMIN' | 'OPERATOR' | 'CUSTOMER';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 9457 problem document. `code` and `fieldErrors` are our extension members. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  fieldErrors?: Record<string, string>;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string;
}

export type ProductStatus = 'ACTIVE' | 'DRAFT' | 'ARCHIVED';

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: number;
  categoryName: string;
  price: number;
  compareAtPrice: number | null;
  imageUrl: string | null;
  status: ProductStatus;
  availableStock: number | null;
  updatedAt: string;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  emailVerified: boolean;
}

export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'EXPIRED';

export interface OrderLine {
  productId: number;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Order {
  orderNumber: string;
  customerEmail: string;
  status: OrderStatus;
  total: number;
  units: number;
  createdAt: string;
  expiresAt: string | null;
  paidAt: string | null;
  items: OrderLine[];
}

export interface CartLine {
  product: Product;
  quantity: number;
}
