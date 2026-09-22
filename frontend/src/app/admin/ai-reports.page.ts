import { DatePipe, JsonPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { errorMessage } from '../core/api.service';
import { AiReport, AiStatus } from './admin.models';
import { AdminService } from './admin.service';
import { renderMarkdown } from './markdown';

@Component({
  selector: 'app-ai-reports-page',
  imports: [DatePipe, JsonPipe],
  template: `
    <div class="page-head">
      <h1>AI reports</h1>
      @if (status(); as s) {
        <span class="status" [attr.data-status]="s.liveProvider ? 'PAID' : 'PENDING_PAYMENT'">
          {{ s.liveProvider ? 'Live: ' + s.provider + ' / ' + s.model : 'Offline mode: rule-based writer' }}
        </span>
      }
    </div>

    <div class="card how">
      <strong>How it works</strong>
      <ol>
        <li>The backend computes the facts from the database: KPIs, sales velocity, days of cover, restock quantities.</li>
        <li>Only those facts are sent to the AI, with instructions to use no other numbers.</li>
        <li>If the provider is down or slow, a rule-based writer answers instead, so the feature never breaks.</li>
      </ol>
    </div>

    <div class="report-actions">
      <div class="segmented">
        <button [class.active]="lang() === 'en'" (click)="lang.set('en')">English</button>
        <button [class.active]="lang() === 'es'" (click)="lang.set('es')">Español</button>
      </div>
      <button class="btn primary" [disabled]="busy()" (click)="generate('inventory')">📦 Inventory health report</button>
      <button class="btn primary" [disabled]="busy()" (click)="generate('sales')">📈 Sales performance report</button>
    </div>

    @if (busy()) {
      <div class="card"><p class="muted">Crunching the numbers and asking the AI…</p></div>
    }
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }

    @if (report(); as r) {
      <article class="card report">
        <div class="report-meta">
          <span class="status">{{ r.type }}</span>
          <span class="muted">{{ r.provider }} / {{ r.model }} · {{ r.latencyMs }} ms · {{ r.generatedAt | date: 'medium' }}</span>
          @if (r.fallback) {
            <span class="status" data-status="LOW">Provider failed: fallback used</span>
          }
        </div>
        <div class="markdown" [innerHTML]="html()"></div>
        <details>
          <summary>Facts sent to the AI (JSON)</summary>
          <pre>{{ r.facts | json }}</pre>
        </details>
      </article>
    }
  `,
})
export class AiReportsPage implements OnInit {
  private readonly admin = inject(AdminService);

  protected readonly status = signal<AiStatus | null>(null);
  protected readonly report = signal<AiReport | null>(null);
  protected readonly lang = signal<'en' | 'es'>('en');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly html = computed(() => renderMarkdown(this.report()?.markdown ?? ''));

  ngOnInit(): void {
    this.admin.aiStatus().subscribe({ next: (s) => this.status.set(s) });
  }

  generate(type: 'inventory' | 'sales'): void {
    this.busy.set(true);
    this.error.set(null);
    this.admin.aiReport(type, this.lang()).subscribe({
      next: (r) => {
        this.report.set(r);
        this.busy.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
