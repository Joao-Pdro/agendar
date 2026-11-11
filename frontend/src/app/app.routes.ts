import { Routes } from '@angular/router';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
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
