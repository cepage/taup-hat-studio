import { Component, ElementRef, inject, signal, OnInit, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PortfolioItem } from '../portfolio.models';
import { PortfolioService } from '../portfolio.service';
import { PortfolioDialog, PortfolioDialogResult } from '../portfolio-dialog/portfolio-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

@Component({
  selector: 'app-portfolio-list',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
  ],
  templateUrl: './portfolio-list.html',
  styleUrl: './portfolio-list.scss',
})
export class PortfolioList implements OnInit {
  private readonly portfolioService = inject(PortfolioService);
  private readonly dialog = inject(MatDialog);

  protected readonly imageInput = viewChild<ElementRef<HTMLInputElement>>('imageInput');

  protected readonly items = signal<PortfolioItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly uploading = signal(false);

  private replaceImageId: number | null = null;

  ngOnInit(): void {
    this.loadItems();
  }

  private loadItems(): void {
    this.loading.set(true);
    this.portfolioService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected createItem(): void {
    const dialogRef = this.dialog.open(PortfolioDialog, {
      data: {
        item: { title: '', description: '', category: '' },
        mode: 'create',
      },
    });
    dialogRef.afterClosed().subscribe((result: PortfolioDialogResult | undefined) => {
      if (result?.file) {
        this.uploading.set(true);
        this.portfolioService
          .create(result.file, result.title, result.description ?? undefined, result.category ?? undefined)
          .subscribe({
            next: () => {
              this.uploading.set(false);
              this.loadItems();
            },
            error: () => this.uploading.set(false),
          });
      }
    });
  }

  protected editItem(item: PortfolioItem): void {
    const dialogRef = this.dialog.open(PortfolioDialog, {
      data: { item: { ...item }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result: PortfolioDialogResult | undefined) => {
      if (result) {
        this.portfolioService.update(item.id!, result).subscribe(() => this.loadItems());
      }
    });
  }

  protected triggerReplaceImage(item: PortfolioItem): void {
    this.replaceImageId = item.id;
    this.imageInput()?.nativeElement.click();
  }

  protected onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || this.replaceImageId == null) return;

    this.uploading.set(true);
    this.portfolioService.updateImage(this.replaceImageId, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.loadItems();
      },
      error: () => this.uploading.set(false),
    });

    input.value = '';
    this.replaceImageId = null;
  }

  protected moveItem(index: number, direction: -1 | 1): void {
    const targetIndex = index + direction;
    const currentItems = [...this.items()];
    if (targetIndex < 0 || targetIndex >= currentItems.length) return;

    [currentItems[index], currentItems[targetIndex]] = [currentItems[targetIndex], currentItems[index]];
    this.items.set(currentItems);

    const orderedIds = currentItems.map((item) => item.id!);
    this.portfolioService.reorder(orderedIds).subscribe({
      next: (reordered) => this.items.set(reordered),
      error: () => this.loadItems(),
    });
  }

  protected confirmDeleteItem(item: PortfolioItem): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Portfolio Item',
        message: `Are you sure you want to delete "${item.title}"? This will also delete the associated images.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.portfolioService.delete(item.id!).subscribe(() => this.loadItems());
      }
    });
  }
}
