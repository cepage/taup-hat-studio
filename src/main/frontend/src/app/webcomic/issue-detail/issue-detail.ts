import { Component, ElementRef, inject, signal, OnInit, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WebcomicIssue, WebcomicPage } from '../webcomic.models';
import { WebcomicService } from '../webcomic.service';
import { IssueDialog } from '../issue-dialog/issue-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-issue-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './issue-detail.html',
  styleUrl: './issue-detail.scss',
})
export class IssueDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly webcomicService = inject(WebcomicService);
  private readonly dialog = inject(MatDialog);

  protected readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected readonly issue = signal<WebcomicIssue | null>(null);
  protected readonly pages = signal<WebcomicPage[]>([]);
  protected readonly seriesTitle = signal('');
  protected readonly loading = signal(true);
  protected readonly uploading = signal(false);
  protected readonly uploadProgress = signal(0);
  protected readonly uploadCurrent = signal(0);
  protected readonly uploadTotal = signal(0);

  protected seriesId!: number;
  private issueId!: number;

  ngOnInit(): void {
    this.seriesId = Number(this.route.snapshot.paramMap.get('seriesId'));
    this.issueId = Number(this.route.snapshot.paramMap.get('issueId'));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    // Load series name for breadcrumb
    this.webcomicService.getSeries(this.seriesId).subscribe({
      next: (series) => this.seriesTitle.set(series.title),
    });
    // Load issue and its pages
    this.webcomicService.getIssue(this.seriesId, this.issueId).subscribe({
      next: (issue) => {
        this.issue.set(issue);
        this.loadPages();
      },
      error: () => this.loading.set(false),
    });
  }

  private loadPages(): void {
    this.webcomicService.listPages(this.seriesId, this.issueId).subscribe({
      next: (pages) => {
        this.pages.set(pages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected editIssue(): void {
    const iss = this.issue();
    if (!iss) return;
    const dialogRef = this.dialog.open(IssueDialog, {
      data: { issue: { ...iss }, mode: 'edit' },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.webcomicService
          .updateIssue(this.seriesId, this.issueId, result)
          .subscribe(() => this.load());
      }
    });
  }

  protected triggerFileUpload(): void {
    this.fileInput()?.nativeElement.click();
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files || files.length === 0) return;

    this.uploading.set(true);
    this.uploadTotal.set(files.length);
    this.uploadCurrent.set(0);
    this.uploadProgress.set(0);

    this.uploadSequentially(Array.from(files), 0);

    // Reset input so the same files can be selected again
    input.value = '';
  }

  private uploadSequentially(files: File[], index: number): void {
    if (index >= files.length) {
      this.uploading.set(false);
      this.loadPages();
      return;
    }

    this.uploadCurrent.set(index + 1);
    this.uploadProgress.set(((index + 1) / files.length) * 100);

    this.webcomicService.uploadPage(this.seriesId, this.issueId, files[index]).subscribe({
      next: () => this.uploadSequentially(files, index + 1),
      error: () => {
        // Continue uploading remaining files on error
        this.uploadSequentially(files, index + 1);
      },
    });
  }

  protected movePage(index: number, direction: -1 | 1): void {
    const targetIndex = index + direction;
    const currentPages = [...this.pages()];
    if (targetIndex < 0 || targetIndex >= currentPages.length) return;

    [currentPages[index], currentPages[targetIndex]] = [currentPages[targetIndex], currentPages[index]];
    this.pages.set(currentPages);

    const orderedIds = currentPages.map((p) => p.id!);
    this.webcomicService.reorderPages(this.seriesId, this.issueId, orderedIds).subscribe({
      next: (reordered) => this.pages.set(reordered),
      error: () => this.loadPages(),
    });
  }

  protected confirmDeletePage(page: WebcomicPage): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Page',
        message: `Are you sure you want to delete page ${page.pageNumber}?`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.webcomicService
          .deletePage(this.seriesId, this.issueId, page.id!)
          .subscribe(() => this.loadPages());
      }
    });
  }
}
