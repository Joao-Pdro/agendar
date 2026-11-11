import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { API_BASE_URL } from '../../../../core/http/api-base-url';
import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { authInterceptor } from '../../../../core/auth/auth.interceptor';
import { AuthService } from '../../../../core/auth/auth.service';
import { LoginPageComponent } from './login-page.component';

/** JWT só com payload legível, para o serviço extrair `role` e `exp`. */
function token(payload: Record<string, unknown>): string {
  const encode = (value: Record<string, unknown>): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.sig`;
}

function setup(queryParams: Record<string, string> = {}) {
  TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '' },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) }, queryParams: of({}) },
      },
    ],
  });

  const fixture = TestBed.createComponent(LoginPageComponent);
  fixture.detectChanges();

  return {
    fixture,
    component: fixture.componentInstance,
    httpMock: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
    auth: TestBed.inject(AuthService),
  };
}

describe('LoginPageComponent', () => {
  beforeEach(() => localStorage.clear());

  it('renderiza os campos de email e senha', () => {
    const { fixture } = setup();

    expect(fixture.nativeElement.querySelector('input[type="email"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="password"]')).not.toBeNull();
  });

  it('faz POST /auth/login e navega para a raiz', () => {
    const { component, httpMock, router, auth } = setup();
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    component.email.set('a@b.com');
    component.password.set('senha');
    component.submit();

    const request = httpMock.expectOne('/auth/login');
    expect(request.request.body).toEqual({ email: 'a@b.com', password: 'senha' });
    request.flush({ token: token({ sub: 'u1', role: 'ADMIN', exp: 1893456000 }) });

    expect(navigate).toHaveBeenCalledWith('/');
    expect(auth.role()).toBe('ADMIN');
    httpMock.verify();
  });

  it('respeita o redirectTo da query string', () => {
    const { component, httpMock, router } = setup({ redirectTo: '/agenda' });
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    component.email.set('a@b.com');
    component.password.set('senha');
    component.submit();

    httpMock
      .expectOne('/auth/login')
      .flush({ token: token({ sub: 'u1', role: 'ADMIN', exp: 1893456000 }) });

    expect(navigate).toHaveBeenCalledWith('/agenda');
    httpMock.verify();
  });

  it('mostra o erro do backend no 401', () => {
    const { component, httpMock, fixture } = setup();

    component.email.set('a@b.com');
    component.password.set('errada');
    component.submit();

    httpMock
      .expectOne('/auth/login')
      .flush(
        {
          title: 'Credenciais inválidas',
          status: 401,
          detail: 'E-mail ou senha incorretos.',
        },
        { status: 401, statusText: 'Unauthorized' },
      );
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Credenciais inválidas');
    expect(alert.textContent).toContain('E-mail ou senha incorretos.');
    httpMock.verify();
  });

  it('envia o tenantId quando preenchido', () => {
    const { component, httpMock } = setup();

    component.email.set('a@b.com');
    component.password.set('senha');
    component.tenantId.set('t1');
    component.submit();

    const request = httpMock.expectOne('/auth/login');
    expect(request.request.body.tenantId).toBe('t1');
    request.flush({ token: token({ sub: 'u1', role: 'ADMIN', exp: 1893456000 }) });
    httpMock.verify();
  });

  it('não dispara duas requisições com o formulário em andamento', () => {
    const { component, httpMock } = setup();

    component.email.set('a@b.com');
    component.password.set('senha');
    component.submit();
    component.submit();

    httpMock.expectOne('/auth/login').flush(null, { status: 500, statusText: 'Error' });
    httpMock.verify();
  });

  it('não navega quando o token vem expirado', () => {
    const { component, httpMock, router, auth } = setup();
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    component.email.set('a@b.com');
    component.password.set('senha');
    component.submit();

    httpMock
      .expectOne('/auth/login')
      .flush({ token: token({ sub: 'u1', role: 'ADMIN', exp: 1000 }) });

    expect(navigate).not.toHaveBeenCalled();
    expect(auth.isAuthenticated()).toBe(false);
    httpMock.verify();
  });
});
