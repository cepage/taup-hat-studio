import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
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
  protected readonly auth = inject(AuthService);
  protected readonly title = signal('TaupHat Studio');

  protected readonly navItems = signal([
    { label: 'Dashboard', icon: 'dashboard', route: '/' },
    { label: 'Webcomics', icon: 'menu_book', route: '/webcomics' },
    { label: 'Portfolio', icon: 'photo_library', route: '/portfolio' },
    { label: 'Theme', icon: 'palette', route: '/theme' },
    { label: 'Publish', icon: 'publish', route: '/publish' },
  ]);
}
