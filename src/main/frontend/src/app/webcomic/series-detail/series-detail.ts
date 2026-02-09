import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WebcomicSeries, WebcomicIssue } from '../webcomic.models';
import { WebcomicService } from '../webcomic.service';
import { SeriesDialog } from '../series-dialog/series-dialog';
import { IssueDialog } from '../issue-dialog/issue-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

@Component({
  selector: 'app-series-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
  ],
  templateUrl: './series-detail.html',
  styleUrl: './series-detail.scss',
})
export class SeriesDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly webcomicService = inject(WebcomicService);
  private readonly dialog = inject(MatDialog);

  protected readonly series = signal<WebcomicSeries | null>(null);
  protected readonly issues = signal<WebcomicIssue[]>([]);
  protected readonly loading = signal(true);

  private seriesId!: number;

  ngOnInit(): void {
    this.seriesId = Number(this.route.snapshot.paramMap.get('seriesId'));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.webcomicService.getSeries(this.seriesId).subscribe({
      next: (series) => {
        this.series.set(series);
        this.webcomicService.listIssues(this.seriesId).subscribe({
          next: (issues) => {
            this.issues.set(issues);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  protected editSeries(): void {
    const s = this.series();
    if (!s) return;
    const dialogRef = this.dialog.open(SeriesDialog, {
      data: { series: { ...s }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService.updateSeries(s.id!, result).subscribe(() => this.load());
      }
    });
  }

  protected createIssue(): void {
    const dialogRef = this.dialog.open(IssueDialog, {
      data: {
        issue: { title: '', published: false, publishDate: null },
        mode: 'create',
      },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService.createIssue(this.seriesId, result).subscribe(() => this.load());
      }
    });
  }

  protected editIssue(issue: WebcomicIssue): void {
    const dialogRef = this.dialog.open(IssueDialog, {
      data: { issue: { ...issue }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService
          .updateIssue(this.seriesId, issue.id!, result)
          .subscribe(() => this.load());
      }
    });
  }

  protected confirmDeleteIssue(issue: WebcomicIssue): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Issue',
        message: `Are you sure you want to delete "${issue.title}"? This will also delete all pages within it.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.webcomicService
          .deleteIssue(this.seriesId, issue.id!)
          .subscribe(() => this.load());
      }
    });
  }
}
