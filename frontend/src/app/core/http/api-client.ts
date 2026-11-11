import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api-base-url';
import type { ApiError } from './api-error';

export interface ApiRequestOptions {
  /** Query string da requisição. Valores `null`/`undefined` são ignorados. */
  params?: Record<string, string | number | boolean | null | undefined>;
}

/**
 * Cliente HTTP da aplicação. Único ponto que fala com o backend.
 *
 * Junta a URL base do ambiente com o caminho do recurso e devolve `ApiError`
 * em caso de falha (o interceptor já converteu a resposta).
 *
 * Nenhuma feature deve injetar `HttpClient` direto: injete `ApiClient`.
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) private readonly baseUrl: string,
  ) {}

  get<T>(path: string, options?: ApiRequestOptions): Observable<T> {
    return this.http.get<T>(this.url(path), { params: this.toParams(options?.params) });
  }

  post<T>(path: string, body: unknown, options?: ApiRequestOptions): Observable<T> {
    return this.http.post<T>(this.url(path), body, {
      params: this.toParams(options?.params),
    });
  }

  put<T>(path: string, body: unknown, options?: ApiRequestOptions): Observable<T> {
    return this.http.put<T>(this.url(path), body, {
      params: this.toParams(options?.params),
    });
  }

  delete<T>(path: string, options?: ApiRequestOptions): Observable<T> {
    return this.http.delete<T>(this.url(path), {
      params: this.toParams(options?.params),
    });
  }

  /** Monta a URL absoluta a partir do caminho relativo ao recurso. */
  private url(path: string): string {
    const base = this.baseUrl.replace(/\/$/, '');
    const resource = path.startsWith('/') ? path : `/${path}`;
    return `${base}${resource}`;
  }

  private toParams(
    params: ApiRequestOptions['params'],
  ): HttpParams | undefined {
    if (!params) {
      return undefined;
    }

    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined) {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return httpParams;
  }
}

export type { ApiError };
