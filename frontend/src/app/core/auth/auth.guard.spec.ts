import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { API_BASE_URL } from '../http/api-base-url';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let router: Router;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthService);
  });

  const runGuard = (url: string): boolean | UrlTree =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as boolean | UrlTree;

  it('bloqueia rota autenticada sem token', () => {
    const result = runGuard('/agenda');

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?redirectTo=%2Fagenda');
  });

  it('libera rota autenticada com sessão válida', () => {
    spyOn(auth, 'isAuthenticated').and.returnValue(true);
    expect(runGuard('/agenda')).toBe(true);
  });
});

describe('guestGuard', () => {
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    auth = TestBed.inject(AuthService);
  });

  const runGuard = (): boolean | UrlTree =>
    TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;

  it('deixa entrar no /login sem sessão', () => {
    expect(runGuard()).toBe(true);
  });

  it('manda usuário logado para a raiz', () => {
    spyOn(auth, 'isAuthenticated').and.returnValue(true);

    const result = runGuard() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
  });
});
