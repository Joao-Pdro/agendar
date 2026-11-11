/** Perfis do MVP, espelhando `br.com.agendar.identity.api.Role` do backend. */
export type Role = 'OPERADOR_PLATAFORMA' | 'ADMIN' | 'RECEPCAO' | 'PRESTADOR';

/** Claims do JWT que o front consome. */
export interface SessionClaims {
  /** Id do usuário (`sub` do token). */
  readonly userId: string;
  /** Tenant do usuário; `null` para o operador da plataforma. */
  readonly tenantId: string | null;
  readonly role: Role;
  /** Expiração em segundos desde a época (`exp`). */
  readonly expiresAt: number;
}

/** Sessão guardada no navegador. */
export interface Session extends SessionClaims {
  readonly token: string;
}

/** Papéis conhecidos, para validar o que veio no token. */
const ROLES: readonly Role[] = ['OPERADOR_PLATAFORMA', 'ADMIN', 'RECEPCAO', 'PRESTADOR'];

function base64UrlDecode(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(
    normalized.length + ((4 - (normalized.length % 4)) % 4),
    '=',
  );
  return atob(padded);
}

/**
 * Lê os claims do JWT sem validar assinatura.
 *
 * O token vem do próprio backend e a assinatura continua sendo verificada a cada
 * requisição no servidor. No cliente, o payload serve só para saber o `role` e a
 * expiração — não é fonte de decisão de segurança.
 *
 * Devolve `null` para token malformado ou sem os claims esperados.
 */
export function decodeToken(token: string): SessionClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }

  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(base64UrlDecode(parts[1])) as Record<string, unknown>;
  } catch {
    return null;
  }

  const userId = payload['sub'];
  const role = payload['role'];
  const exp = payload['exp'];
  const tenantId = payload['tenantId'];

  if (typeof userId !== 'string' || typeof role !== 'string' || typeof exp !== 'number') {
    return null;
  }
  if (!ROLES.includes(role as Role)) {
    return null;
  }

  return {
    userId,
    tenantId: typeof tenantId === 'string' ? tenantId : null,
    role: role as Role,
    expiresAt: exp,
  };
}

/** `true` quando a expiração (em segundos) já passou. */
export function isExpired(
  claims: Pick<SessionClaims, 'expiresAt'>,
  now: Date = new Date(),
): boolean {
  return claims.expiresAt * 1000 <= now.getTime();
}
