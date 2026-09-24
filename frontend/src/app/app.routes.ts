import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { roleGuard } from './core/auth.guard';
import { AuthService } from './core/auth.service';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./store/catalog.page').then((m) => m.CatalogPage), title: 'Game Store' },
  { path: 'cart', loadComponent: () => import('./store/cart.page').then((m) => m.CartPage), title: 'Cart · Game Store' },
  {
    path: 'orders',
    canActivate: [roleGuard()],
    loadComponent: () => import('./store/orders.page').then((m) => m.OrdersPage),
    title: 'My orders · Game Store',
  },
  { path: 'login', loadComponent: () => import('./auth/login.page').then((m) => m.LoginPage), title: 'Sign in · Game Store' },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN', 'OPERATOR')],
    loadComponent: () => import('./admin/admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => (inject(AuthService).isAdmin() ? 'dashboard' : 'inventory') },
      {
        path: 'dashboard',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./admin/dashboard.page').then((m) => m.DashboardPage),
        title: 'Dashboard · Game Store',
      },
      {
        path: 'inventory',
        loadComponent: () => import('./admin/inventory.page').then((m) => m.InventoryPage),
        title: 'Inventory · Game Store',
      },
      {
        path: 'products',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./admin/products.page').then((m) => m.ProductsPage),
        title: 'Products · Game Store',
      },
      {
        path: 'ai',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./admin/ai-reports.page').then((m) => m.AiReportsPage),
        title: 'AI reports · Game Store',
      },
      {
        path: 'audit',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./admin/audit.page').then((m) => m.AuditPage),
        title: 'Audit log · Game Store',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
