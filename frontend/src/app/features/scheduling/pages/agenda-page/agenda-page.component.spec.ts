import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { API_BASE_URL } from '../../../../core/http/api-base-url';
import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { AgendaPageComponent } from './agenda-page.component';

describe('AgendaPageComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgendaPageComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function create() {
    const fixture = TestBed.createComponent(AgendaPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('pede o prestador antes de carregar a agenda', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('Informe o id de um prestador');
  });

  it('carrega slots reais ao aplicar o prestador', () => {
    const fixture = create();
    fixture.componentInstance.providerIdInput.set('p1');
    fixture.componentInstance.applyProvider();
    fixture.detectChanges();

    const request = httpMock.expectOne((req) => req.url === '/providers/p1/slots');
    request.flush([{ startsAt: '2026-03-10T09:00:00-03:00', endsAt: '2026-03-10T10:00:00-03:00' }]);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.slots().length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('button.day-grid__slot').length).toBe(1);
  });

  it('abre o formulário ao clicar num slot', () => {
    const fixture = create();
    fixture.componentInstance.providerIdInput.set('p1');
    fixture.componentInstance.applyProvider();
    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/providers/p1/slots')
      .flush([{ startsAt: '2026-03-10T09:00:00-03:00', endsAt: '2026-03-10T10:00:00-03:00' }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button.day-grid__slot') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedSlot()).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('app-appointment-form'),
    ).not.toBeNull();
  });

  it('alterna entre visão diária e semanal', () => {
    const fixture = create();
    fixture.componentInstance.providerIdInput.set('p1');
    fixture.componentInstance.applyProvider();
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/providers/p1/slots').flush([]);

    expect(fixture.nativeElement.querySelector('app-day-grid')).not.toBeNull();

    fixture.componentInstance.setView('semana');
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/providers/p1/slots').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-week-grid')).not.toBeNull();
    expect(fixture.componentInstance.weekDays().length).toBe(7);
  });

  it('mostra a mensagem de conflito do 409 no formulário', () => {
    const fixture = create();
    fixture.componentInstance.providerIdInput.set('p1');
    fixture.componentInstance.applyProvider();
    fixture.detectChanges();
    httpMock
      .expectOne((req) => req.url === '/providers/p1/slots')
      .flush([{ startsAt: '2026-03-10T09:00:00-03:00', endsAt: '2026-03-10T10:00:00-03:00' }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button.day-grid__slot') as HTMLButtonElement).click();
    fixture.detectChanges();

    fixture.componentInstance.confirmBooking();
    httpMock.expectOne('/appointments').flush(
      {
        title: 'Horário indisponível',
        status: 409,
        detail: 'O prestador já tem agendamento nesse intervalo.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.appointment-form__conflict');
    expect(alert.textContent).toContain('Horário indisponível');
    expect(alert.textContent).toContain('já tem agendamento nesse intervalo');
  });

  it('avisa que os agendamentos estão em modo de exemplo', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('modo de exemplo');
  });
});
