import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppointmentFormComponent } from './appointment-form.component';
import type { ApiError } from '../../../core/http/api-error';
import type { Slot } from '../models/scheduling';

describe('AppointmentFormComponent', () => {
  let fixture: ComponentFixture<AppointmentFormComponent>;

  const slot: Slot = {
    startsAt: '2026-03-10T13:00:00Z',
    endsAt: '2026-03-10T14:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppointmentFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentFormComponent);
    fixture.componentRef.setInput('slot', slot);
    fixture.componentRef.setInput('idempotencyKey', 'chave-123');
  });

  it('mostra o intervalo do slot', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Novo agendamento');
    expect(fixture.componentInstance.slotLabel).toContain('–');
  });

  it('mostra title e detail do 409 de conflito', () => {
    const conflict: ApiError = {
      status: 409,
      title: 'Horário indisponível',
      detail: 'O prestador já tem agendamento nesse intervalo.',
    };
    fixture.componentRef.setInput('error', conflict);
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Horário indisponível');
    expect(alert.textContent).toContain('O prestador já tem agendamento nesse intervalo.');
    expect(fixture.componentInstance.conflictMessage).toBe(
      'Horário indisponível: O prestador já tem agendamento nesse intervalo.',
    );
  });

  it('não trata 409 como conflito quando não há erro', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance.conflictMessage).toBeNull();
    expect(fixture.componentInstance.otherError).toBeNull();
  });

  it('mostra erro que não é conflito', () => {
    fixture.componentRef.setInput('error', { status: 500, title: 'Erro no servidor' } as ApiError);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Erro no servidor');
  });

  it('emite confirm e cancel', () => {
    fixture.detectChanges();

    let confirmed = 0;
    let cancelled = 0;
    fixture.componentInstance.confirmed.subscribe(() => (confirmed += 1));
    fixture.componentInstance.dismissed.subscribe(() => (cancelled += 1));

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.nativeElement.querySelector('button[type="button"]').click();

    expect(confirmed).toBe(1);
    expect(cancelled).toBe(1);
  });

  it('desabilita o confirmar durante o envio', () => {
    fixture.componentRef.setInput('submitting', true);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Agendando');
  });
});
