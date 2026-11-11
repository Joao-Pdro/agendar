import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { ApiError, networkApiError, toApiError } from './api-error';

/**
 * Converte toda falha HTTP em `ApiError`.
 *
 * - Resposta com corpo ProblemDetail (`application/problem+json`) vira `{ status, title, detail }`.
 * - Resposta sem corpo vira erro genérico pelo status.
 * - Falha de rede (status 0) vira `networkApiError()`.
 *
 * Erros que não são `HttpErrorResponse` passam adiante sem alteração.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      const apiError: ApiError =
        error.status === 0 ? networkApiError() : toApiError(error.status, error.error);

      return throwError(() => apiError);
    }),
  );
