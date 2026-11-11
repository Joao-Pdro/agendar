import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { ApiClient } from '../http/api-client';
import type { ApiError } from '../http/api-error';
import { SessionStorage } from './session-storage';
import { Session, SessionClaims, decodeToken, isExpired } from './session';

/** Corpo do `POST /auth/login` (backend `AuthController.LoginRequest`). */
export interface LoginRequest {
  readonly tenantId?: string | null;
  readonly email: string;
  readonly password: string;
}

interface LoginResponse {
  readonly token: string;
}

/** Resultado do login: ou a sessão, ou o erro que a tela deve mostrar. */
export interface LoginResult {
  readonly session: Session | null;
  readonly error: ApiError | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);
  private readonly storage = inject(SessionStorage);
  private readonly router = inject(Router);

  private readonly _session = signal<Session | null>(this.restore());
  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly role = computed(() => this._session()?.role ?? null);

  /**
   * Autentica com `POST /auth/login` e guarda a sessão.
   *
   * Devolve `null` em caso de falha, com o `ApiError` disponível para a tela.
   */
  login(request: LoginRequest): Observable<LoginResult> {
    const body: LoginRequest = {
      email: request.email,
      password: request.password,
      ...(request.tenantId ? { tenantId: request.tenantId } : {}),
    };

    return this.api.post<LoginResponse>('/auth/login', body).pipe(
      map((response) => this.buildSession(response.token)),
      tap((session) => {
        if (session) {
          this.persist(session);
        }
      }),
      map((session) => ({
        session,
        error: session ? null : ({ status: 401, title: 'Sessão inválida' } as ApiError),
      })),
      catchError((error: ApiError) => of({ session: null, error })),
    );
  }

  logout(): void {
    this.storage.clear();
    this._session.set(null);
  }

  /**
   * Limpa a sessão e leva para a tela de login, guardando a rota de origem.
   * Chamado pelo interceptor quando qualquer resposta vem 401.
   */
  redirectToLogin(currentUrl?: string): void {
    this.logout();
    const queryParams = currentUrl ? { redirectTo: currentUrl } : undefined;
    void this.router.navigate(['/login'], { queryParams });
  }

  token(): string | null {
    return this._session()?.token ?? null;
  }

  private buildSession(token: string): Session | null {
    const claims = decodeToken(token);
    if (!claims || isExpired(claims)) {
      return null;
    }
    return { ...claims, token };
  }

  private persist(session: Session): void {
    this.storage.save(session);
    this._session.set(session);
  }

  /** Recupera a sessão do armazenamento, descartando token expirado. */
  private restore(): Session | null {
    const stored = this.storage.load();
    if (!stored) {
      return null;
    }

    const claims: SessionClaims | null = decodeToken(stored.token);
    if (!claims || isExpired(claims)) {
      this.storage.clear();
      return null;
    }

    return { ...claims, token: stored.token };
  }
}

export type { ApiError };
