import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoading()) {
    // Wait briefly for auth check to complete, then re-evaluate
    return new Promise<boolean>((resolve) => {
      const check = setInterval(() => {
        if (!auth.isLoading()) {
          clearInterval(check);
          if (auth.isAuthenticated()) {
            resolve(true);
          } else {
            auth.login();
            resolve(false);
          }
        }
      }, 100);
    });
  }

  if (auth.isAuthenticated()) {
    return true;
  }

  auth.login();
  return false;
};
