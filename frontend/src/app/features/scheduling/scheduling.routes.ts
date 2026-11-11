import { Routes } from '@angular/router';

export const SCHEDULING_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/agenda-page/agenda-page.component').then(
        (m) => m.AgendaPageComponent,
      ),
    title: 'Agenda',
  },
];
