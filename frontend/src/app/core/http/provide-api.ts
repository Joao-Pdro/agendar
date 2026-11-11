import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { authInterceptor } from '../auth/auth.interceptor';
import { API_BASE_URL } from './api-base-url';
import { errorInterceptor } from './error.interceptor';

/**
 * Registra o cliente HTTP da aplicação: `HttpClient` com o interceptor de erro
 * (converte ProblemDetail em `ApiError`) e o de sessão (anexa o token e trata
 * 401), mais a URL base vinda do ambiente.
 *
 * A ordem importa: `errorInterceptor` roda antes, para o `authInterceptor`
 * receber o erro já tipado.
 */
export function provideApi(apiBaseUrl: string): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
    { provide: API_BASE_URL, useValue: apiBaseUrl },
  ]);
}
