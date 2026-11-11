import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';

import { API_BASE_URL } from '../http/api-base-url';
import { errorInterceptor } from '../http/error.interceptor';
import { ApiClient } from '../http/api-client';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { fakeToken } from './auth.service.spec';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  async function loginAs(role = 'ADMIN'): Promise<void> {
    await new Promise<void>((resolve) => {
      auth.login({ email: 'a@b.com', password: 's' }).subscribe(() => resolve());
      httpMock.expectOne('/auth/login').flush({
        token: fakeToken({ sub: 'u1', role, tenantId: 't1', exp: 1893456000 }),
      });
    });
  }

  it('anexa Authorization: Bearer em toda chamada à API', async () => {
    await loginAs();
    const api = TestBed.inject(ApiClient);

    api.get('/providers/p1/slots').subscribe();
    const request = httpMock.expectOne('/providers/p1/slots');

    const header = request.request.headers.get('Authorization');
    expect(header).toMatch(/^Bearer /);
    expect(header).toBe(`Bearer ${auth.token()}`);
    request.flush([]);
  });

  it('não anexa header quando não há sessão', () => {
    TestBed.inject(HttpClient).get('/publico').subscribe();
    const request = httpMock.expectOne('/publico');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('401 de qualquer chamada leva a /login', async () => {
    await loginAs();
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    TestBed.inject(ApiClient).get('/providers/p1/slots').subscribe({
      error: () => undefined,
    });
    httpMock
      .expectOne('/providers/p1/slots')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(
      ['/login'],
      jasmine.objectContaining({ queryParams: jasmine.anything() }),
    );
  });

  it('401 do próprio login não redireciona', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    auth.login({ email: 'a@b.com', password: 'errada' }).subscribe();
    httpMock
      .expectOne('/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });
});
