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

  it('lista agendamentos do prestador passando o intervalo', () => {
    api
      .listAppointments('p1', '2026-01-01T00:00:00Z', '2026-01-07T23:59:59Z')
      .subscribe((appointments) => expect(appointments.length).toBe(1));

    const request = httpMock.expectOne(
      (req) => req.url === '/scheduling/appointments',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('providerId')).toBe('p1');
    request.flush([
      {
        id: 'a1',
        providerId: 'p1',
        startsAt: '2026-01-02T13:00:00Z',
        endsAt: '2026-01-02T14:00:00Z',
        status: 'CONFIRMADO',
      },
    ]);
  });
});
