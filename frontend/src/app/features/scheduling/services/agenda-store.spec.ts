import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { API_BASE_URL } from '../../../core/http/api-base-url';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { AgendaStore, toIsoDate } from './agenda-store';
import { newIdempotencyKey } from './idempotency-key';

describe('AgendaStore', () => {
  let store: AgendaStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    store = TestBed.inject(AgendaStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('carrega slots do prestador e do dia selecionados', () => {
    store.selectedProviderId.set('p1');
    store.selectedDate.set('2026-03-10');
    store.loadSlots();

    const request = httpMock.expectOne((req) => req.url === '/providers/p1/slots');
    expect(request.request.params.get('date')).toBe('2026-03-10');
    request.flush([{ startsAt: '2026-03-10T09:00:00-03:00', endsAt: '2026-03-10T10:00:00-03:00' }]);

    expect(store.slots().length).toBe(1);
    expect(store.loading()).toBe(false);
  });

  it('não busca slots sem prestador selecionado', () => {
    store.loadSlots();
    httpMock.expectNone('/providers//slots');
    expect(store.slots()).toEqual([]);
  });

  it('guarda o ApiError quando o load falha', () => {
    store.selectedProviderId.set('p1');
    store.loadSlots();

    httpMock
      .expectOne((req) => req.url === '/providers/p1/slots')
      .flush({ title: 'Prestador não encontrado', status: 404 }, { status: 404, statusText: 'Not Found' });

    expect(store.error()?.status).toBe(404);
    expect(store.error()?.title).toBe('Prestador não encontrado');
  });

  it('reusa a mesma Idempotency-Key em retry', () => {
    store.selectedProviderId.set('p1');
    const key = newIdempotencyKey();
    const slot = { startsAt: '2026-03-10T13:00:00Z', endsAt: '2026-03-10T14:00:00Z' };

    store.createAppointment(slot, key).subscribe();
    const first = httpMock.expectOne('/appointments');
    expect(first.request.headers.get('Idempotency-Key')).toBe(key);
    first.flush(null, { status: 500, statusText: 'Server Error' });

    // retry com a mesma chave
    store.createAppointment(slot, key).subscribe();
    const second = httpMock.expectOne('/appointments');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(null, { status: 409, statusText: 'Conflict' });

    expect(store.error()?.status).toBe(409);
  });

  it('exibe title e detail do 409 de conflito', () => {
    store.selectedProviderId.set('p1');
    store
      .createAppointment(
        { startsAt: '2026-03-10T13:00:00Z', endsAt: '2026-03-10T14:00:00Z' },
        newIdempotencyKey(),
      )
      .subscribe();

    httpMock.expectOne('/appointments').flush(
      {
        title: 'Horário indisponível',
        status: 409,
        detail: 'O prestador já tem agendamento nesse intervalo.',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(store.error()?.title).toBe('Horário indisponível');
    expect(store.error()?.detail).toBe('O prestador já tem agendamento nesse intervalo.');
  });
});

describe('toIsoDate', () => {
  it('formata a data local como YYYY-MM-DD', () => {
    expect(toIsoDate(new Date(2026, 2, 10))).toBe('2026-03-10');
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});

describe('newIdempotencyKey', () => {
  it('gera valores diferentes a cada chamada', () => {
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey());
  });

  it('cabe no limite de 128 caracteres do contrato', () => {
    expect(newIdempotencyKey().length).toBeLessThanOrEqual(128);
  });
});
