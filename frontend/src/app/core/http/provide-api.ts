import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { API_BASE_URL } from './api-base-url';
import { errorInterceptor } from './error.interceptor';

/**
 * Registra o cliente HTTP da aplicação: `HttpClient` com o interceptor de erro
 * e a URL base vinda do ambiente.
 */
export function provideApi(apiBaseUrl: string): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(withInterceptors([errorInterceptor])),
    { provide: API_BASE_URL, useValue: apiBaseUrl },
  ]);
}
