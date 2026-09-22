import { Injectable, signal } from '@angular/core';

/** Last API instance that answered (X-Served-By header): shows the load balancer at work. */
@Injectable({ providedIn: 'root' })
export class InstanceService {
  readonly servedBy = signal<string | null>(null);
  readonly seen = signal<string[]>([]);

  record(instance: string | null): void {
    if (!instance) {
      return;
    }
    this.servedBy.set(instance);
    if (!this.seen().includes(instance)) {
      this.seen.update((list) => [...list, instance]);
    }
  }
}
