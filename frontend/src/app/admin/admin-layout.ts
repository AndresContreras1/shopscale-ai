import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="admin">
      <aside class="admin-nav">
        <strong class="muted">BACK OFFICE</strong>
        @if (auth.isAdmin()) {
          <a routerLink="dashboard" routerLinkActive="active">📊 Dashboard</a>
        }
        <a routerLink="inventory" routerLinkActive="active">📦 Inventory</a>
        @if (auth.isAdmin()) {
          <a routerLink="products" routerLinkActive="active">🏷️ Products</a>
          <a routerLink="ai" routerLinkActive="active">✨ AI reports</a>
          <a routerLink="audit" routerLinkActive="active">🛡️ Audit log</a>
        }
      </aside>
      <section class="admin-content">
        <router-outlet />
      </section>
    </div>
  `,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
}
