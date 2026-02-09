import { Component, inject, signal, effect } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly breakpointObserver = inject(BreakpointObserver);
  protected readonly auth = inject(AuthService);
  protected readonly title = signal('TaupHat Studio');

  protected readonly isMobile = signal(false);
  protected readonly sidenavOpened = signal(true);

  protected readonly navItems = signal([
    { label: 'Dashboard', icon: 'dashboard', route: '/' },
    { label: 'Webcomics', icon: 'menu_book', route: '/webcomics' },
    { label: 'Portfolio', icon: 'photo_library', route: '/portfolio' },
    { label: 'Theme', icon: 'palette', route: '/theme' },
    { label: 'Publish', icon: 'publish', route: '/publish' },
  ]);

  constructor() {
    this.breakpointObserver
      .observe([Breakpoints.Handset, Breakpoints.Tablet])
      .subscribe(result => {
        this.isMobile.set(result.matches);
        this.sidenavOpened.set(!result.matches);
      });
  }

  protected toggleSidenav(): void {
    this.sidenavOpened.set(!this.sidenavOpened());
  }
}
