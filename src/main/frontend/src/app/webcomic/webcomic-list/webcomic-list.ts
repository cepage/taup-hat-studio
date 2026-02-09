import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WebcomicSeries } from '../webcomic.models';
import { WebcomicService } from '../webcomic.service';
import { SeriesDialog } from '../series-dialog/series-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-webcomic-list',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './webcomic-list.html',
  styleUrl: './webcomic-list.scss',
})
export class WebcomicList implements OnInit {
  private readonly webcomicService = inject(WebcomicService);
  private readonly dialog = inject(MatDialog);

  protected readonly series = signal<WebcomicSeries[]>([]);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.loadSeries();
  }

  private loadSeries(): void {
    this.loading.set(true);
    this.webcomicService.listSeries().subscribe({
      next: (series) => {
        this.series.set(series);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected createSeries(): void {
    const dialogRef = this.dialog.open(SeriesDialog, {
      data: {
        series: { title: '', slug: '', description: '', active: true, sortOrder: this.series().length },
        mode: 'create',
      },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService.createSeries(result).subscribe(() => this.loadSeries());
      }
    });
  }

  protected editSeries(series: WebcomicSeries): void {
    const dialogRef = this.dialog.open(SeriesDialog, {
      data: { series: { ...series }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService.updateSeries(series.id!, result).subscribe(() => this.loadSeries());
      }
    });
  }

  protected confirmDeleteSeries(series: WebcomicSeries): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Series',
        message: `Are you sure you want to delete "${series.title}"? This will also delete all issues and pages within it.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.webcomicService.deleteSeries(series.id!).subscribe(() => this.loadSeries());
      }
    });
  }
}
