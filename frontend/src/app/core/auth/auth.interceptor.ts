import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_BASE_URL } from '../http/api-base-url';
import { ApiError } from '../http/api-error';
import { AuthService } from './auth.service';

/**
 * Anexa `Authorization: Bearer <token>` em toda chamada à API e trata sessão expirada.
 *
 * - Só adiciona o header para requisições destinadas à URL base da API; chamadas
 *   para outros hosts não recebem o token.
 * - Resposta 401 (que não venha do próprio login) limpa a sessão e leva para
 *   `/login`, guardando a rota atual.
 * - A conversão de resposta de erro em `ApiError` continua no `errorInterceptor`;
 *   este roda depois dele na cadeia.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const baseUrl = inject(API_BASE_URL);

  const token = auth.token();
  const authorized =
    token !== null && isApiRequest(request.url, baseUrl)
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authorized).pipe(
    catchError((error: ApiError) => {
      if (error.status === 401 && !isLoginRequest(request.url)) {
        auth.redirectToLogin(router.url);
      }
      return throwError(() => error);
    }),
  );
};

/** `true` quando a requisição é o próprio login: 401 ali é senha errada, não sessão expirada. */
function isLoginRequest(url: string): boolean {
  return url.endsWith('/auth/login');
}

/** `true` quando a URL pertence à API configurada. */
function isApiRequest(url: string, baseUrl: string): boolean {
  if (baseUrl === '') {
    return true;
  }
  return url.startsWith(baseUrl);
}
