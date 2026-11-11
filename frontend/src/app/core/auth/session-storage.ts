import { Injectable } from '@angular/core';

import { Session } from './session';

const STORAGE_KEY = 'agendar.session';

/**
 * Persistência da sessão no `localStorage`, para o login sobreviver a um reload.
 *
 * Isolado num serviço próprio para o `AuthService` não conhecer a API de
 * armazenamento e os testes poderem trocar por um dublê.
 */
@Injectable({ providedIn: 'root' })
export class SessionStorage {
  load(): Session | null {
    const raw = this.storage()?.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as Partial<Session>;
      if (
        typeof parsed.token !== 'string' ||
        typeof parsed.userId !== 'string' ||
        typeof parsed.role !== 'string' ||
        typeof parsed.expiresAt !== 'number'
      ) {
        return null;
      }
      return {
        token: parsed.token,
        userId: parsed.userId,
        tenantId: typeof parsed.tenantId === 'string' ? parsed.tenantId : null,
        role: parsed.role,
        expiresAt: parsed.expiresAt,
      };
    } catch {
      return null;
    }
  }

  save(session: Session): void {
    this.storage()?.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  clear(): void {
    this.storage()?.removeItem(STORAGE_KEY);
  }

  /** `null` quando não há `localStorage` (SSR, teste sem DOM, navegação privada). */
  private storage(): Storage | null {
    try {
      return globalThis.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
