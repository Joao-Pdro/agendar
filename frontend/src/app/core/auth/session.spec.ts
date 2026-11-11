import { decodeToken, isExpired } from './session';

/** Monta um JWT só com payload legível: a assinatura não é validada no cliente. */
function fakeToken(payload: Record<string, unknown>): string {
  const encode = (value: Record<string, unknown>): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.assinatura`;
}

describe('decodeToken', () => {
  it('lê sub, role, tenantId e exp', () => {
    const token = fakeToken({
      sub: 'user-1',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      exp: 1893456000,
    });

    expect(decodeToken(token)).toEqual({
      userId: 'user-1',
      tenantId: 'tenant-1',
      role: 'ADMIN',
      expiresAt: 1893456000,
    });
  });

  it('aceita token de operador da plataforma sem tenantId', () => {
    const token = fakeToken({ sub: 'user-2', role: 'OPERADOR_PLATAFORMA', exp: 1893456000 });
    expect(decodeToken(token)?.tenantId).toBeNull();
  });

  it('rejeita token malformado', () => {
    expect(decodeToken('nao-e-token')).toBeNull();
    expect(decodeToken('a.b')).toBeNull();
    expect(decodeToken('a.b.c')).toBeNull();
  });

  it('rejeita payload sem claims obrigatórios', () => {
    expect(decodeToken(fakeToken({ sub: 'user-1', exp: 1 }))).toBeNull();
    expect(decodeToken(fakeToken({ sub: 'user-1', role: 'ADMIN' }))).toBeNull();
  });

  it('rejeita role desconhecido', () => {
    const token = fakeToken({ sub: 'u', role: 'SUPER_ADMIN', exp: 1893456000 });
    expect(decodeToken(token)).toBeNull();
  });
});

describe('isExpired', () => {
  it('considera expirado quando exp já passou', () => {
    const claims = { expiresAt: 1000 };
    expect(isExpired(claims, new Date(1_000_000))).toBe(true);
  });

  it('considera válido antes do exp', () => {
    const claims = { expiresAt: 2000 };
    expect(isExpired(claims, new Date(1_000_000))).toBe(false);
  });
});

export { fakeToken };
