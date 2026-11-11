import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section>
      <h1>Agendar</h1>
      <p>Base do aplicativo. Comece pela agenda.</p>
      <a routerLink="/agenda">Ir para a agenda</a>
    </section>
  `,
})
export class HomePageComponent {}
