import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { API_BASE_URL } from '../http/api-base-url';
import { errorInterceptor } from '../http/error.interceptor';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { SessionStorage } from './session-storage';

/** Monta um JWT só com payload legível: a assinatura não é validada no cliente. */
function fakeToken(payload: Record<string, unknown>): string {
  const encode = (value: Record<string, unknown>): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.assinatura`;
}

function configure() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '' },
    ],
  });
  return {
    auth: TestBed.inject(AuthService),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());

  it('guarda a sessão com token e role depois do login', () => {
    const { auth, httpMock } = configure();

    const roles: string[] = [];
    auth.login({ email: 'a@b.com', password: 'senha', tenantId: null }).subscribe((result) => {
      roles.push(result.session?.role ?? 'SEM_ROLE');
      expect(result.error).toBeNull();
    });

    const request = httpMock.expectOne('/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'a@b.com', password: 'senha' });
    request.flush({
      token: fakeToken({ sub: 'u1', role: 'PRESTADOR', tenantId: 't1', exp: 1893456000 }),
    });

    expect(roles).toEqual(['PRESTADOR']);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.role()).toBe('PRESTADOR');
    expect(localStorage.getItem('agendar.session')).not.toBeNull();
    httpMock.verify();
  });

  it('envia tenantId quando informado', () => {
    const { auth, httpMock } = configure();

    auth.login({ email: 'a@b.com', password: 'senha', tenantId: 't1' }).subscribe();
    const request = httpMock.expectOne('/auth/login');
    expect(request.request.body.tenantId).toBe('t1');
    request.flush({ token: fakeToken({ sub: 'u1', role: 'ADMIN', exp: 1893456000 }) });
    httpMock.verify();
  });

  it('devolve o ApiError e mantém deslogado no 401', () => {
    const { auth, httpMock } = configure();

    let error: unknown = null;
    auth.login({ email: 'a@b.com', password: 'errada' }).subscribe((result) => {
      error = result.error;
      expect(result.session).toBeNull();
    });

    httpMock
      .expectOne('/auth/login')
      .flush(
        { title: 'Credenciais inválidas', status: 401 },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(error).toEqual({ status: 401, title: 'Credenciais inválidas' });
    expect(auth.isAuthenticated()).toBe(false);
    httpMock.verify();
  });

  it('limpa a sessão no logout', () => {
    const { auth, httpMock } = configure();

    auth.login({ email: 'a@b.com', password: 's' }).subscribe();
    httpMock
      .expectOne('/auth/login')
      .flush({ token: fakeToken({ sub: 'u1', role: 'ADMIN', exp: 1893456000 }) });
    expect(auth.isAuthenticated()).toBe(true);

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('agendar.session')).toBeNull();
    httpMock.verify();
  });

  it('restaura a sessão válida do armazenamento', () => {
    localStorage.setItem(
      'agendar.session',
      JSON.stringify({
        token: fakeToken({ sub: 'u1', role: 'RECEPCAO', tenantId: 't1', exp: 1893456000 }),
        userId: 'u1',
        tenantId: 't1',
        role: 'RECEPCAO',
        expiresAt: 1893456000,
      }),
    );

    const { auth } = configure();

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.role()).toBe('RECEPCAO');
  });

  it('descarta token expirado guardado no armazenamento', () => {
    localStorage.setItem(
      'agendar.session',
      JSON.stringify({
        token: fakeToken({ sub: 'u1', role: 'ADMIN', exp: 1000 }),
        userId: 'u1',
        tenantId: null,
        role: 'ADMIN',
        expiresAt: 1000,
      }),
    );

    const { auth } = configure();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('agendar.session')).toBeNull();
  });
});

describe('SessionStorage', () => {
  beforeEach(() => localStorage.clear());

  it('ignora conteúdo que não é JSON', () => {
    localStorage.setItem('agendar.session', 'nao-json');
    expect(new SessionStorage().load()).toBeNull();
  });

  it('ignora objeto sem os campos obrigatórios', () => {
    localStorage.setItem('agendar.session', JSON.stringify({ token: 'x' }));
    expect(new SessionStorage().load()).toBeNull();
  });
});

export { fakeToken };
