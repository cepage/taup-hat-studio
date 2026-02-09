import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'webcomics',
        loadComponent: () =>
          import('./webcomic/webcomic-list/webcomic-list').then((m) => m.WebcomicList),
      },
      {
        path: 'webcomics/:seriesId',
        loadComponent: () =>
          import('./webcomic/series-detail/series-detail').then((m) => m.SeriesDetail),
      },
      {
        path: 'webcomics/:seriesId/issues/:issueId',
        loadComponent: () =>
          import('./webcomic/issue-detail/issue-detail').then((m) => m.IssueDetail),
      },
      {
        path: 'portfolio',
        loadComponent: () =>
          import('./portfolio/portfolio-list/portfolio-list').then((m) => m.PortfolioList),
      },
      {
        path: 'theme',
        loadComponent: () =>
          import('./theme/theme-editor/theme-editor').then((m) => m.ThemeEditor),
      },
      {
        path: 'publish',
        loadComponent: () => import('./publish/publish').then((m) => m.Publish),
      },
    ],
  },
];
