import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CommissionRequest } from '../commission.models';
import { CommissionService } from '../commission.service';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

const SUBJECT_PREFIX = /^Commission request\s*[—-]\s*/;

@Component({
  selector: 'app-commission-inbox',
  imports: [
    DatePipe,
    TitleCasePipe,
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    EmptyStateComponent,
  ],
  templateUrl: './commission-inbox.html',
  styleUrl: './commission-inbox.scss',
})
export class CommissionInbox implements OnInit {
  private readonly commissionService = inject(CommissionService);
  private readonly dialog = inject(MatDialog);

  protected readonly requests = signal<CommissionRequest[]>([]);
  protected readonly loading = signal(true);
  protected readonly retryingId = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.commissionService.list().subscribe({
      next: (requests) => {
        this.requests.set(requests);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected retry(request: CommissionRequest, event: Event): void {
    event.stopPropagation();
    this.retryingId.set(request.id);
    this.commissionService.retry(request.id).subscribe({
      next: (updated) => {
        this.requests.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
        this.retryingId.set(null);
      },
      error: () => this.retryingId.set(null),
    });
  }

  protected delete(request: CommissionRequest, event: Event): void {
    event.stopPropagation();
    const unsent = request.status !== 'SENT' ? ' It has not been delivered yet and will not be sent.' : '';
    this.dialog
      .open(ConfirmDialog, {
        data: {
          title: 'Delete Commission Request',
          message: `Are you sure you want to delete "${this.title(request)}"? This cannot be undone.${unsent}`,
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.commissionService.delete(request.id).subscribe(() =>
            this.requests.update((list) => list.filter((r) => r.id !== request.id)),
          );
        }
      });
  }

  protected title(request: CommissionRequest): string {
    return request.subject?.replace(SUBJECT_PREFIX, '') || 'Commission request';
  }

  protected replyLink(request: CommissionRequest): string | null {
    if (!request.replyTo?.includes('@') || request.replyTo.startsWith('@')) return null;
    const subject = encodeURIComponent(`Re: ${request.subject ?? 'Your commission request'}`);
    return `mailto:${request.replyTo}?subject=${subject}`;
  }
}
