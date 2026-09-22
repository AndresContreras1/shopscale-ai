import { Routes } from '@angular/router';
import { roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./store/catalog.page').then((m) => m.CatalogPage), title: 'ShopScale' },
  { path: 'cart', loadComponent: () => import('./store/cart.page').then((m) => m.CartPage), title: 'Cart · ShopScale' },
  {
    path: 'orders',
    canActivate: [roleGuard()],
    loadComponent: () => import('./store/orders.page').then((m) => m.OrdersPage),
    title: 'My orders · ShopScale',
  },
  { path: 'login', loadComponent: () => import('./auth/login.page').then((m) => m.LoginPage), title: 'Sign in · ShopScale' },
  { path: '**', redirectTo: '' },
];
