import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { errorMessage } from '../core/api.service';
import { Page } from '../core/models';
import { AuditEntry } from './admin.models';
import { AdminService } from './admin.service';

@Component({
  selector: 'app-audit-page',
  imports: [DatePipe],
  template: `
    <h1>Audit log</h1>
    <p class="muted">Who changed what and when: logins, failed logins, price changes, stock corrections, AI usage.</p>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    @if (page(); as p) {
      <div class="card">
        <table class="table">
          <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
          <tbody>
            @for (a of p.content; track a.id) {
              <tr>
                <td>{{ a.createdAt | date: 'short' }}</td>
                <td>{{ a.actor }}</td>
                <td><span class="status" [attr.data-status]="a.action === 'LOGIN_FAILED' ? 'CRITICAL' : ''">{{ a.action }}</span></td>
                <td>{{ a.entityType }} {{ a.entityId }}</td>
                <td>{{ a.details }}</td>
              </tr>
            }
          </tbody>
        </table>
        <div class="pager">
          <button class="btn small" [disabled]="p.page === 0" (click)="load(p.page - 1)">←</button>
          <span>{{ p.page + 1 }} / {{ p.totalPages || 1 }}</span>
          <button class="btn small" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">→</button>
        </div>
      </div>
    }
  `,
})
export class AuditPage implements OnInit {
  private readonly admin = inject(AdminService);
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(index: number): void {
    this.admin.audit(index).subscribe({ next: (p) => this.page.set(p), error: (e) => this.error.set(errorMessage(e)) });
  }
}
