import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  SiteConfig,
  SocialLink,
  HEADING_FONTS,
  BODY_FONTS,
  SOCIAL_PLATFORMS,
} from '../site-config.models';
import { SiteConfigService } from '../site-config.service';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-theme-editor',
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './theme-editor.html',
  styleUrl: './theme-editor.scss',
})
export class ThemeEditor implements OnInit {
  private readonly siteConfigService = inject(SiteConfigService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected readonly config = signal<SiteConfig | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly uploadingHero = signal(false);
  protected readonly socialLinks = signal<SocialLink[]>([]);

  protected readonly headingFonts = HEADING_FONTS;
  protected readonly bodyFonts = BODY_FONTS;
  protected readonly socialPlatforms = SOCIAL_PLATFORMS;

  ngOnInit(): void {
    this.loadConfig();
  }

  private loadConfig(): void {
    this.loading.set(true);
    this.siteConfigService.get().subscribe({
      next: (cfg) => {
        this.config.set(cfg);
        this.parseSocialLinks(cfg.socialLinks);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load theme settings.', 'Dismiss', { duration: 5000 });
      },
    });
  }

  protected saveConfig(): void {
    const cfg = this.config();
    if (!cfg) return;

    // Serialize social links back to JSON
    cfg.socialLinks = this.serializeSocialLinks();

    this.saving.set(true);
    this.siteConfigService.update(cfg).subscribe({
      next: (updated) => {
        this.config.set(updated);
        this.saving.set(false);
        this.snackBar.open('Theme settings saved.', 'OK', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Failed to save theme settings.', 'Dismiss', { duration: 5000 });
      },
    });
  }

  protected onHeroImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploadingHero.set(true);
    this.siteConfigService.uploadHeroImage(file).subscribe({
      next: (updated) => {
        this.config.set(updated);
        this.uploadingHero.set(false);
        this.snackBar.open('Hero image uploaded.', 'OK', { duration: 3000 });
      },
      error: () => {
        this.uploadingHero.set(false);
        this.snackBar.open('Failed to upload hero image.', 'Dismiss', { duration: 5000 });
      },
    });

    input.value = '';
  }

  protected removeHeroImage(): void {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Remove Hero Image',
        message: 'Are you sure you want to remove the hero image? This will delete it from storage.',
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.uploadingHero.set(true);
        this.siteConfigService.deleteHeroImage().subscribe({
          next: (updated) => {
            this.config.set(updated);
            this.uploadingHero.set(false);
            this.snackBar.open('Hero image removed.', 'OK', { duration: 3000 });
          },
          error: () => {
            this.uploadingHero.set(false);
            this.snackBar.open('Failed to remove hero image.', 'Dismiss', { duration: 5000 });
          },
        });
      }
    });
  }

  // ── Social Links ────────────────────────────────────────────────────────────

  protected addSocialLink(): void {
    this.socialLinks.update((links) => [...links, { platform: '', url: '' }]);
  }

  protected removeSocialLink(index: number): void {
    this.socialLinks.update((links) => links.filter((_, i) => i !== index));
  }

  private parseSocialLinks(json: string | null): void {
    if (!json) {
      this.socialLinks.set([]);
      return;
    }
    try {
      const parsed = JSON.parse(json) as SocialLink[];
      this.socialLinks.set(Array.isArray(parsed) ? parsed : []);
    } catch {
      this.socialLinks.set([]);
    }
  }

  private serializeSocialLinks(): string | null {
    const links = this.socialLinks().filter((l) => l.platform && l.url);
    return links.length > 0 ? JSON.stringify(links) : null;
  }
}
