import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { provideApi } from './core/http/provide-api';
import { AuthService } from './core/auth/auth.service';

/** JWT só com payload legível: o serviço extrai `role` e `exp` daqui. */
function token(payload: Record<string, unknown>): string {
  const encode = (value: Record<string, unknown>): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.sig`;
}

/** Semeia uma sessão válida no armazenamento antes do serviço ser criado. */
function seedSession(): void {
  const jwt = token({ sub: 'u1', role: 'ADMIN', tenantId: 't1', exp: 1893456000 });
  localStorage.setItem(
    'agendar.session',
    JSON.stringify({
      token: jwt,
      userId: 'u1',
      tenantId: 't1',
      role: 'ADMIN',
      expiresAt: 1893456000,
    }),
  );
}

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    seedSession();

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      // A rota /agenda carrega a agenda real, que depende do HttpClient.
      providers: [provideRouter(routes), provideApi(''), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the shell on the root route', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('app-shell')).not.toBeNull();
    expect(root.querySelector('app-shell h1')?.textContent).toContain('Agendar');
  });

  it('should lazy load the scheduling feature on /agenda', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/agenda');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('app-agenda-page')).not.toBeNull();
    expect(root.querySelector('app-agenda-page h1')?.textContent).toContain('Agenda');
  });

  it('manda para /login quando não há sessão', async () => {
    localStorage.clear();
    const fixture = TestBed.createComponent(AppComponent);
    const router = TestBed.inject(Router);

    // serviço criado antes já leu a sessão; recria sem sessão
    TestBed.inject(AuthService).logout();

    await router.navigateByUrl('/agenda');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toContain('/login');
    expect(fixture.nativeElement.querySelector('app-login-page')).not.toBeNull();
  });
});
