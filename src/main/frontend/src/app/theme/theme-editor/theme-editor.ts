import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-theme-editor',
  imports: [MatIconModule],
  template: `
    <h1>Site Theme</h1>
    <p>Theme customization will be implemented in Phase 4.</p>
  `,
  styles: `
    :host { display: block; }
    h1 { font: var(--mat-sys-headline-medium); margin: 0 0 8px; }
    p { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class ThemeEditor {}
