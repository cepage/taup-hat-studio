import { Component, ElementRef, inject, signal, computed, OnInit, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatBadgeModule } from '@angular/material/badge';
import { PortfolioItem, PortfolioSet } from '../portfolio.models';
import { PortfolioService } from '../portfolio.service';
import { PortfolioDialog, PortfolioDialogResult } from '../portfolio-dialog/portfolio-dialog';
import { PortfolioSetDialog, PortfolioSetDialogResult } from '../portfolio-set-dialog/portfolio-set-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { forkJoin } from 'rxjs';

type GridEntry =
  | { kind: 'item'; item: PortfolioItem }
  | { kind: 'set'; set: PortfolioSet };

@Component({
  selector: 'app-portfolio-list',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatBadgeModule,
    EmptyStateComponent,
  ],
  templateUrl: './portfolio-list.html',
  styleUrl: './portfolio-list.scss',
})
export class PortfolioList implements OnInit {
  private readonly portfolioService = inject(PortfolioService);
  private readonly dialog = inject(MatDialog);

  protected readonly imageInput = viewChild<ElementRef<HTMLInputElement>>('imageInput');
  protected readonly iconInput = viewChild<ElementRef<HTMLInputElement>>('iconInput');

  protected readonly items = signal<PortfolioItem[]>([]);
  protected readonly sets = signal<PortfolioSet[]>([]);
  protected readonly loading = signal(true);
  protected readonly uploading = signal(false);
  protected readonly expandedSetId = signal<number | null>(null);

  protected readonly standaloneItems = computed(() => this.items().filter((i) => i.setId == null));

  protected readonly gridEntries = computed<GridEntry[]>(() => {
    const standalone = this.standaloneItems().map(
      (item) => ({ kind: 'item' as const, item, order: item.sortOrder }),
    );
    const setEntries = this.sets().map(
      (set) => ({ kind: 'set' as const, set, order: set.sortOrder }),
    );
    return [...standalone, ...setEntries].sort((a, b) => a.order - b.order);
  });

  private replaceImageId: number | null = null;
  private replaceIconSetId: number | null = null;

  ngOnInit(): void {
    this.loadAll();
  }

  private loadAll(): void {
    this.loading.set(true);
    forkJoin({
      items: this.portfolioService.list(),
      sets: this.portfolioService.listSets(),
    }).subscribe({
      next: ({ items, sets }) => {
        this.items.set(items);
        this.sets.set(sets);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // ── Standalone Item Actions ──

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
              this.loadAll();
            },
            error: () => this.uploading.set(false),
          });
      }
    });
  }

  protected createItemInSet(set: PortfolioSet): void {
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
          .create(
            result.file,
            result.title,
            result.description ?? undefined,
            result.category ?? undefined,
            set.id!,
          )
          .subscribe({
            next: () => {
              this.uploading.set(false);
              this.loadAll();
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
        this.portfolioService.update(item.id!, result).subscribe(() => this.loadAll());
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
        this.loadAll();
      },
      error: () => this.uploading.set(false),
    });

    input.value = '';
    this.replaceImageId = null;
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
        this.portfolioService.delete(item.id!).subscribe(() => this.loadAll());
      }
    });
  }

  protected removeItemFromSet(item: PortfolioItem, set: PortfolioSet): void {
    const currentItems = this.getSetItems(set);
    const remainingIds = currentItems.filter((i) => i.id !== item.id).map((i) => i.id!);
    this.portfolioService.setSetItems(set.id!, remainingIds).subscribe(() => this.loadAll());
  }

  protected moveSetItem(set: PortfolioSet, index: number, direction: -1 | 1): void {
    const setItems = [...this.getSetItems(set)];
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= setItems.length) return;

    [setItems[index], setItems[targetIndex]] = [setItems[targetIndex], setItems[index]];
    const orderedIds = setItems.map((item) => item.id!);
    this.portfolioService.setSetItems(set.id!, orderedIds).subscribe(() => this.loadAll());
  }

  // ── Set Actions ──

  protected createSet(): void {
    const dialogRef = this.dialog.open(PortfolioSetDialog, {
      data: {
        set: { title: '', description: '' },
        mode: 'create',
      },
    });
    dialogRef.afterClosed().subscribe((result: PortfolioSetDialogResult | undefined) => {
      if (result?.file) {
        this.uploading.set(true);
        this.portfolioService
          .createSet(result.file, result.title, result.description ?? undefined)
          .subscribe({
            next: () => {
              this.uploading.set(false);
              this.loadAll();
            },
            error: () => this.uploading.set(false),
          });
      }
    });
  }

  protected editSet(set: PortfolioSet): void {
    const dialogRef = this.dialog.open(PortfolioSetDialog, {
      data: { set: { ...set }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result: PortfolioSetDialogResult | undefined) => {
      if (result) {
        this.portfolioService.updateSet(set.id!, result).subscribe(() => this.loadAll());
      }
    });
  }

  protected triggerReplaceIcon(set: PortfolioSet): void {
    this.replaceIconSetId = set.id;
    this.iconInput()?.nativeElement.click();
  }

  protected onIconSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || this.replaceIconSetId == null) return;

    this.uploading.set(true);
    this.portfolioService.updateSetIcon(this.replaceIconSetId, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.loadAll();
      },
      error: () => this.uploading.set(false),
    });

    input.value = '';
    this.replaceIconSetId = null;
  }

  protected confirmDeleteSet(set: PortfolioSet): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Portfolio Set',
        message: `Are you sure you want to delete the set "${set.title}"? Items in this set will be ungrouped, not deleted.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.portfolioService.deleteSet(set.id!).subscribe(() => this.loadAll());
      }
    });
  }

  protected toggleSetExpanded(set: PortfolioSet): void {
    if (this.expandedSetId() === set.id) {
      this.expandedSetId.set(null);
    } else {
      this.expandedSetId.set(set.id);
      // Load set details with items if not already loaded
      this.portfolioService.getSet(set.id!).subscribe((loaded) => {
        this.sets.update((sets) => sets.map((s) => (s.id === loaded.id ? loaded : s)));
      });
    }
  }

  protected getSetItems(set: PortfolioSet): PortfolioItem[] {
    return this.items()
      .filter((i) => i.setId === set.id)
      .sort((a, b) => a.setSortOrder - b.setSortOrder);
  }

  protected addItemToSet(item: PortfolioItem, set: PortfolioSet): void {
    const currentItems = this.getSetItems(set);
    const orderedIds = [...currentItems.map((i) => i.id!), item.id!];
    this.portfolioService.setSetItems(set.id!, orderedIds).subscribe(() => this.loadAll());
  }

  protected getAvailableItemsForSet(set: PortfolioSet): PortfolioItem[] {
    return this.items().filter((i) => i.setId == null);
  }
}
