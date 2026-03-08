import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'watched',
        loadComponent: () =>
          import('./features/watched/watched.component').then((m) => m.WatchedComponent),
      },
      {
        path: 'picks',
        loadComponent: () =>
          import('./features/picks/picks.component').then((m) => m.PicksComponent),
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('./features/chat/chat.component').then((m) => m.ChatComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
