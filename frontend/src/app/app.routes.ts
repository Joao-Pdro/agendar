import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/pages/login-page/login-page.component').then(
        (m) => m.LoginPageComponent,
      ),
    title: 'Entrar',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/home/pages/home-page/home-page.component').then(
            (m) => m.HomePageComponent,
          ),
        title: 'Agendar',
      },
      {
        path: 'agenda',
        loadChildren: () =>
          import('./features/scheduling/scheduling.routes').then(
            (m) => m.SCHEDULING_ROUTES,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
