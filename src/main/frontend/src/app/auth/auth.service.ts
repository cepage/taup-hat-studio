import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface UserInfo {
  name: string;
  email: string;
  picture?: string;
  localMode?: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly user = signal<UserInfo | null>(null);
  private readonly loading = signal(true);

  readonly currentUser = this.user.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  constructor(private readonly http: HttpClient) {
    this.loadUser();
  }

  private loadUser(): void {
    this.http.get<UserInfo>('/api/auth/user').subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: () => {
        this.user.set(null);
        this.loading.set(false);
      },
    });
  }

  login(): void {
    window.location.href = '/oauth2/authorization/google';
  }

  logout(): void {
    this.http.post('/api/logout', {}).subscribe({
      next: () => {
        this.user.set(null);
        window.location.href = '/';
      },
      error: () => {
        window.location.href = '/';
      },
    });
  }
}
