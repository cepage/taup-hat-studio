import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-portfolio-list',
  imports: [MatIconModule],
  template: `
    <h1>Portfolio</h1>
    <p>Portfolio management will be implemented in Phase 3.</p>
  `,
  styles: `
    :host { display: block; }
    h1 { font: var(--mat-sys-headline-medium); margin: 0 0 8px; }
    p { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class PortfolioList {}
