import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { WebcomicSeries } from '../webcomic.models';

export interface SeriesDialogData {
  series: Partial<WebcomicSeries>;
  mode: 'create' | 'edit';
}

@Component({
  selector: 'app-series-dialog',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'create' ? 'New Series' : 'Edit Series' }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline">
        <mat-label>Title</mat-label>
        <input matInput [(ngModel)]="data.series.title" required />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Slug</mat-label>
        <input matInput [(ngModel)]="data.series.slug" required placeholder="my-series" />
        <mat-hint>URL-friendly identifier</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Description</mat-label>
        <textarea matInput [(ngModel)]="data.series.description" rows="3"></textarea>
      </mat-form-field>

      <mat-slide-toggle [(ngModel)]="data.series.active">Active</mat-slide-toggle>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        [disabled]="!data.series.title || !data.series.slug"
        (click)="save()"
      >
        {{ data.mode === 'create' ? 'Create' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-width: 400px;
      padding-top: 24px !important;
    }

    mat-form-field {
      width: 100%;
    }
  `,
})
export class SeriesDialog {
  protected readonly data = inject<SeriesDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<SeriesDialog>);

  protected save(): void {
    this.dialogRef.close(this.data.series);
  }
}
