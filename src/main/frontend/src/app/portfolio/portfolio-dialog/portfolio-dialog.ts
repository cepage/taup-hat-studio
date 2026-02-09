import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PortfolioItem } from '../portfolio.models';

export interface PortfolioDialogData {
  item: Partial<PortfolioItem>;
  mode: 'create' | 'edit';
}

export interface PortfolioDialogResult {
  title: string;
  description: string | null;
  category: string | null;
  file?: File;
}

@Component({
  selector: 'app-portfolio-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'create' ? 'New Portfolio Item' : 'Edit Portfolio Item' }}</h2>
    <mat-dialog-content>
      @if (data.mode === 'create') {
        <div class="file-input">
          <label class="file-label" [class.has-file]="selectedFile">
            {{ selectedFile ? selectedFile.name : 'Choose an image...' }}
            <input type="file" accept="image/*" (change)="onFileSelected($event)" hidden />
          </label>
        </div>
      }

      <mat-form-field appearance="outline">
        <mat-label>Title</mat-label>
        <input matInput [(ngModel)]="data.item.title" required />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Description</mat-label>
        <textarea matInput [(ngModel)]="data.item.description" rows="3"></textarea>
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Category</mat-label>
        <input matInput [(ngModel)]="data.item.category" placeholder="e.g. illustration, commission" />
        <mat-hint>Used for filtering on the portfolio page</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [disabled]="!canSave()" (click)="save()">
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

    .file-input {
      margin-bottom: 8px;
    }

    .file-label {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
      border: 2px dashed var(--mat-sys-outline-variant);
      border-radius: 12px;
      cursor: pointer;
      font: var(--mat-sys-body-large);
      color: var(--mat-sys-on-surface-variant);
      transition:
        border-color 0.2s,
        background 0.2s;

      &:hover {
        border-color: var(--mat-sys-primary);
        background: var(--mat-sys-surface-container-low);
      }

      &.has-file {
        border-color: var(--mat-sys-primary);
        color: var(--mat-sys-on-surface);
      }
    }
  `,
})
export class PortfolioDialog {
  protected readonly data = inject<PortfolioDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<PortfolioDialog>);

  protected selectedFile: File | null = null;

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  protected canSave(): boolean {
    if (!this.data.item.title) return false;
    if (this.data.mode === 'create' && !this.selectedFile) return false;
    return true;
  }

  protected save(): void {
    const result: PortfolioDialogResult = {
      title: this.data.item.title!,
      description: this.data.item.description ?? null,
      category: this.data.item.category ?? null,
    };
    if (this.selectedFile) {
      result.file = this.selectedFile;
    }
    this.dialogRef.close(result);
  }
}
