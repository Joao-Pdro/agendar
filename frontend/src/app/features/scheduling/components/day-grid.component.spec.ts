import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayGridComponent } from './day-grid.component';
import type { Appointment, Slot } from '../models/scheduling';

describe('DayGridComponent', () => {
  let fixture: ComponentFixture<DayGridComponent>;

  const slot: Slot = {
    startsAt: '2026-03-10T09:00:00-03:00',
    endsAt: '2026-03-10T10:00:00-03:00',
  };

  const appointment: Appointment = {
    id: 'a1',
    tenantId: 't1',
    providerId: 'p1',
    customerId: 'c1',
    serviceId: 's1',
    startsAt: '2026-03-10T14:00:00-03:00',
    endsAt: '2026-03-10T15:00:00-03:00',
    status: 'CONFIRMADO',
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DayGridComponent] }).compileComponents();
    fixture = TestBed.createComponent(DayGridComponent);
    fixture.componentRef.setInput('date', '2026-03-10');
  });

  it('lista os slots livres', () => {
    fixture.componentRef.setInput('slots', [slot]);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button.day-grid__slot');
    expect(buttons.length).toBe(1);
  });

  it('emite o slot ao clicar', () => {
    fixture.componentRef.setInput('slots', [slot]);
    fixture.detectChanges();

    const emitted: Slot[] = [];
    fixture.componentInstance.slotSelected.subscribe((value) => emitted.push(value));

    (fixture.nativeElement.querySelector('button.day-grid__slot') as HTMLButtonElement).click();

    expect(emitted).toEqual([slot]);
  });

  it('mostra os agendamentos com status', () => {
    fixture.componentRef.setInput('appointments', [appointment]);
    fixture.detectChanges();

    const item = fixture.nativeElement.querySelector('.day-grid__appointment');
    expect(item.textContent).toContain('CONFIRMADO');
    expect(item.getAttribute('data-status')).toBe('CONFIRMADO');
  });

  it('mantém o offset recebido no horário', () => {
    fixture.componentRef.setInput('appointments', [appointment]);
    fixture.detectChanges();

    expect(fixture.componentInstance.offsetOf(appointment.startsAt)).toBe('-03:00');
  });

  it('avisa quando o dia está vazio', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum horário livre');
  });
});
