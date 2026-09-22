export type StockHealth = 'STOCKOUT' | 'CRITICAL' | 'LOW' | 'HEALTHY' | 'OVERSTOCK' | 'NO_SALES';

export interface ProductInsight {
  productId: number;
  sku: string;
  name: string;
  category: string;
  price: number;
  available: number;
  reorderPoint: number;
  unitsSold30d: number;
  revenue30d: number;
  dailyVelocity: number;
  daysOfCover: number | null;
  suggestedReorderQty: number;
  health: StockHealth;
}

export interface Dashboard {
  sales: {
    revenue30d: number;
    revenuePrev30d: number;
    revenueGrowthPct: number;
    orders30d: number;
    ordersPrev30d: number;
    averageOrderValue: number;
    pendingPaymentOrders: number;
  };
  inventory: {
    activeProducts: number;
    stockouts: number;
    critical: number;
    low: number;
    overstock: number;
    noSales: number;
    inventoryValue: number;
  };
  dailySales: { date: string; orders: number; revenue: number }[];
  categories: { category: string; units: number; revenue: number }[];
  topSellers: ProductInsight[];
  restockNow: ProductInsight[];
  slowMovers: ProductInsight[];
}

export interface InventoryRow {
  productId: number;
  sku: string;
  productName: string;
  onHand: number;
  reserved: number;
  available: number;
  reorderPoint: number;
  lowStock: boolean;
  updatedAt: string;
}

export interface Movement {
  id: number;
  type: 'RECEIPT' | 'ADJUSTMENT' | 'RESERVATION' | 'RELEASE' | 'SALE';
  quantity: number;
  onHandAfter: number;
  reservedAfter: number;
  reason: string;
  reference: string | null;
  createdBy: string;
  createdAt: string;
}

export interface FlashSaleResult {
  sku: string;
  buyers: number;
  unitsPerBuyer: number;
  availableBefore: number;
  successfulReservations: number;
  rejectedNoStock: number;
  unitsReserved: number;
  minAvailableObserved: number;
  oversold: boolean;
  elapsedMs: number;
}

export interface AiReport {
  type: 'INVENTORY' | 'SALES' | 'PRODUCT_DESCRIPTION';
  provider: string;
  model: string;
  fallback: boolean;
  latencyMs: number;
  generatedAt: string;
  markdown: string;
  facts: unknown;
}

export interface AiStatus {
  provider: string;
  model: string;
  liveProvider: boolean;
}

export interface AuditEntry {
  id: number;
  actor: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  createdAt: string;
}
