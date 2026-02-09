import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../shared/confirm-dialog/confirm-dialog';
import { PublishService } from './publish.service';

@Component({
  selector: 'app-publish',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
  ],
  templateUrl: './publish.html',
  styleUrl: './publish.scss',
})
export class Publish {
  private readonly publishService = inject(PublishService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly previewing = signal(false);
  readonly deploying = signal(false);
  readonly previewUrl = signal<string | null>(null);
  readonly lastPreview = signal<{ fileCount: number; timestamp: string } | null>(null);
  readonly lastDeploy = signal<{ fileCount: number; timestamp: string } | null>(null);
  readonly error = signal<string | null>(null);

  generatePreview(): void {
    this.previewing.set(true);
    this.error.set(null);

    this.publishService.generatePreview().subscribe({
      next: (response) => {
        this.previewing.set(false);
        if (response.status === 'success') {
          this.previewUrl.set(response.previewUrl ?? null);
          this.lastPreview.set({
            fileCount: response.fileCount,
            timestamp: response.timestamp,
          });
          this.snackBar.open('Preview deployed successfully', 'Dismiss', { duration: 5000 });
        } else {
          this.error.set(response.message ?? 'Preview generation failed');
          this.snackBar.open('Preview failed: ' + (response.message ?? 'Unknown error'), 'Dismiss', {
            duration: 8000,
          });
        }
      },
      error: (err) => {
        this.previewing.set(false);
        const message = err.error?.message ?? err.message ?? 'Preview deployment failed';
        this.error.set(message);
        this.snackBar.open('Preview failed: ' + message, 'Dismiss', { duration: 8000 });
      },
    });
  }

  deployToProduction(): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Deploy to Production',
        message:
          'This will publish the site to production. Are you sure you want to continue?',
        confirmLabel: 'Deploy',
      } satisfies ConfirmDialogData,
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;

      this.deploying.set(true);
      this.error.set(null);

      this.publishService.deployToProduction().subscribe({
        next: (response) => {
          this.deploying.set(false);
          if (response.status === 'success') {
            this.lastDeploy.set({
              fileCount: response.fileCount ?? 0,
              timestamp: response.timestamp,
            });
            this.snackBar.open('Site deployed to production successfully!', 'Dismiss', {
              duration: 5000,
            });
          } else {
            this.error.set(response.message ?? 'Deployment failed');
            this.snackBar.open('Deploy failed: ' + (response.message ?? 'Unknown error'), 'Dismiss', {
              duration: 8000,
            });
          }
        },
        error: (err) => {
          this.deploying.set(false);
          const message = err.error?.message ?? err.message ?? 'Production deployment failed';
          this.error.set(message);
          this.snackBar.open('Deploy failed: ' + message, 'Dismiss', { duration: 8000 });
        },
      });
    });
  }
}
