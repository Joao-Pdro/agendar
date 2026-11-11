import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import type { ApiError } from '../../../../core/http/api-error';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly email = signal('');
  readonly password = signal('');
  /** Tenant é opcional no backend: sem ele, o login procura o usuário pelo e-mail. */
  readonly tenantId = signal('');

  readonly submitting = signal(false);
  readonly error = signal<ApiError | null>(null);

  submit(): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth
      .login({
        email: this.email().trim(),
        password: this.password(),
        tenantId: this.tenantId().trim() || null,
      })
      .subscribe((result) => {
        this.submitting.set(false);

        if (!result.session) {
          this.error.set(result.error ?? FALLBACK_ERROR);
          return;
        }

        const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo') ?? '/';
        void this.router.navigateByUrl(redirectTo);
      });
  }
}

const FALLBACK_ERROR: ApiError = {
  status: 401,
  title: 'Não foi possível entrar',
  detail: 'Confira o e-mail e a senha.',
};
