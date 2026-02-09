import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-publish',
  imports: [MatIconModule],
  template: `
    <h1>Publish Site</h1>
    <p>Site publishing will be implemented in Phase 6.</p>
  `,
  styles: `
    :host { display: block; }
    h1 { font: var(--mat-sys-headline-medium); margin: 0 0 8px; }
    p { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Publish {}
