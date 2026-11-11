import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { API_BASE_URL } from './api-base-url';
import { ApiClient } from './api-client';
import { errorInterceptor } from './error.interceptor';

describe('ApiClient', () => {
  let client: ApiClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.exemplo.com/' },
      ],
    });
    client = TestBed.inject(ApiClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('monta a URL com a base do ambiente, sem barra dupla', () => {
    client.get('/scheduling/slots').subscribe();
    const request = httpMock.expectOne('https://api.exemplo.com/scheduling/slots');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('aceita caminho sem barra inicial', () => {
    client.get('/health').subscribe();
    httpMock.expectOne('https://api.exemplo.com/health').flush({});
  });

  it('envia params ignorando valores nulos', () => {
    client
      .get('/scheduling/slots', { params: { providerId: 'p1', date: '2026-01-01', extra: null } })
      .subscribe();

    const request = httpMock.expectOne(
      (req) => req.url === 'https://api.exemplo.com/scheduling/slots',
    );
    expect(request.request.params.get('providerId')).toBe('p1');
    expect(request.request.params.get('date')).toBe('2026-01-01');
    expect(request.request.params.has('extra')).toBe(false);
    request.flush({});
  });

  it('propaga ApiError já tipado em falha', async () => {
    const error = await new Promise<unknown>((resolve) => {
      client.post('/scheduling/appointments', {}).subscribe({
        next: () => fail('esperava erro'),
        error: (e: unknown) => resolve(e),
      });
      httpMock
        .expectOne('https://api.exemplo.com/scheduling/appointments')
        .flush({ title: 'Horário já ocupado', status: 409 }, { status: 409, statusText: 'Conflict' });
    });

    expect(error).toEqual({ status: 409, title: 'Horário já ocupado' });
  });
});
