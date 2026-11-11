import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

import type { ApiError } from '../../../core/http/api-error';
import { environment } from '../../../../environments/environment';
import type { Appointment, CreateAppointmentRequest, Slot } from '../models/scheduling';
import { MOCK_APPOINTMENTS, MOCK_CUSTOMER_ID, MOCK_SERVICE } from './mock-agenda';
import { SchedulingApi } from './scheduling-api';

/** Duração usada para calcular slots na tela, até existir catálogo (#19). */
export const DEFAULT_SERVICE_DURATION_MINUTES = 60;

@Injectable({ providedIn: 'root' })
export class AgendaStore {
  private readonly api = inject(SchedulingApi);

  readonly selectedProviderId = signal<string>('');
  readonly selectedDate = signal<string>(toIsoDate(new Date()));
  readonly view = signal<'dia' | 'semana'>('dia');

  readonly slots = signal<Slot[]>([]);
  readonly appointments = signal<Appointment[]>([]);
  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);

  /** `true` quando a grade está com agendamentos mockados (falta o GET de agendamentos). */
  readonly usingMockAppointments = environment.useMockAgenda;

  /**
   * Slots reais do contrato (`GET /providers/{id}/slots`).
   * Erro de rede é mostrado na tela, não engolido.
   */
  loadSlots(): void {
    const providerId = this.selectedProviderId();
    if (providerId === '') {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.api
      .listFreeSlots(providerId, this.selectedDate(), DEFAULT_SERVICE_DURATION_MINUTES)
      .subscribe({
        next: (slots) => {
          this.slots.set(slots);
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.slots.set([]);
          this.error.set(error);
          this.loading.set(false);
        },
      });
  }

  /**
   * Agendamentos da grade.
   *
   * TODO(#20): trocar pelo `GET /appointments?providerId&from&to` quando a issue
   * do endpoint existir. Hoje vem de mock atrás da flag `useMockAgenda`.
   */
  loadAppointments(): void {
    if (!this.usingMockAppointments) {
      this.appointments.set([]);
      return;
    }
    this.appointments.set(
      MOCK_APPOINTMENTS.filter((appointment) => this.isInCurrentRange(appointment)),
    );
  }

  /**
   * Cria o agendamento reaproveitando a mesma `Idempotency-Key` em retries.
   * A chave só é renovada depois de um sucesso, para um clique duplo não gerar
   * dois agendamentos.
   */
  createAppointment(slot: Slot, key: string): Observable<Appointment | null> {
    const body: CreateAppointmentRequest = {
      providerId: this.selectedProviderId(),
      // TODO(#20): ids reais quando existirem o catálogo (#19) e a issue de clientes.
      customerId: MOCK_CUSTOMER_ID,
      serviceId: MOCK_SERVICE.id,
      startsAt: slot.startsAt,
    };

    this.error.set(null);

    return this.api.createAppointment(body, key).pipe(
      tap((appointment) => {
        this.appointments.update((current) => [...current, appointment]);
        this.loadSlots();
      }),
      catchError((error: ApiError) => {
        this.error.set(error);
        return of(null);
      }),
    );
  }

  private isInCurrentRange(appointment: Appointment): boolean {
    const date = this.selectedDate();
    return appointment.startsAt.startsWith(date);
  }
}

/** Data local no formato `YYYY-MM-DD`, que é o esperado pelo contrato de slots. */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}
