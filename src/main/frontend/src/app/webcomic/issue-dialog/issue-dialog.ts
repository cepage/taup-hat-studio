import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { provideNativeDateAdapter } from '@angular/material/core';
import { WebcomicIssue } from '../webcomic.models';

export interface IssueDialogData {
  issue: Partial<WebcomicIssue>;
  mode: 'create' | 'edit';
}

@Component({
  selector: 'app-issue-dialog',
  providers: [provideNativeDateAdapter()],
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSlideToggleModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'create' ? 'New Issue' : 'Edit Issue' }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline">
        <mat-label>Title</mat-label>
        <input matInput [(ngModel)]="data.issue.title" required />
      </mat-form-field>

      @if (data.mode === 'edit') {
        <mat-form-field appearance="outline">
          <mat-label>Issue Number</mat-label>
          <input matInput type="number" [(ngModel)]="data.issue.issueNumber" min="1" required />
        </mat-form-field>
      }

      <mat-form-field appearance="outline">
        <mat-label>Publish Date</mat-label>
        <input matInput [matDatepicker]="picker" [(ngModel)]="data.issue.publishDate" />
        <mat-datepicker-toggle matIconSuffix [for]="picker" />
        <mat-datepicker #picker />
      </mat-form-field>

      <mat-slide-toggle [(ngModel)]="data.issue.published">Published</mat-slide-toggle>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [disabled]="!data.issue.title" (click)="save()">
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
export class IssueDialog {
  protected readonly data = inject<IssueDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<IssueDialog>);

  protected save(): void {
    this.dialogRef.close(this.data.issue);
  }
}
