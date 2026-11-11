import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { API_BASE_URL } from '../../../core/http/api-base-url';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SchedulingApi } from './scheduling-api';

describe('SchedulingApi', () => {
  let api: SchedulingApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    api = TestBed.inject(SchedulingApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lista slots livres no caminho e com os params do contrato', () => {
    api.listFreeSlots('p1', '2026-03-10', 60).subscribe((slots) => {
      expect(slots.length).toBe(2);
      expect(slots[0].startsAt).toBe('2026-03-10T09:00:00-03:00');
    });

    const request = httpMock.expectOne((req) =>
      req.url === '/providers/p1/slots',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('date')).toBe('2026-03-10');
    expect(request.request.params.get('durationMinutes')).toBe('60');
    request.flush([
      { startsAt: '2026-03-10T09:00:00-03:00', endsAt: '2026-03-10T10:00:00-03:00' },
      { startsAt: '2026-03-10T10:00:00-03:00', endsAt: '2026-03-10T11:00:00-03:00' },
    ]);
  });

  it('envia a Idempotency-Key no POST de agendamento', () => {
    const body = {
      providerId: 'p1',
      customerId: 'c1',
      serviceId: 's1',
      startsAt: '2026-03-10T13:00:00Z',
    };

    api.createAppointment(body, 'chave-123').subscribe();

    const request = httpMock.expectOne('/appointments');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('chave-123');
    expect(request.request.body).toEqual(body);
    request.flush({ id: 'a1' }, { status: 201, statusText: 'Created' });
  });

  it('propaga o 409 de conflito já tipado como ApiError', async () => {
    const error = await new Promise<unknown>((resolve) => {
      api.createAppointment(
        { providerId: 'p1', customerId: 'c1', serviceId: 's1', startsAt: '2026-03-10T13:00:00Z' },
        'chave-123',
      ).subscribe({
        next: () => fail('esperava erro'),
        error: (e: unknown) => resolve(e),
      });

      httpMock.expectOne('/appointments').flush(
        {
          title: 'Horário indisponível',
          status: 409,
          detail: 'O prestador já tem agendamento nesse intervalo.',
        },
        { status: 409, statusText: 'Conflict' },
      );
    });

    expect(error).toEqual({
      status: 409,
      title: 'Horário indisponível',
      detail: 'O prestador já tem agendamento nesse intervalo.',
    });
  });
});
