import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../../core/http/api-client';
import type { ApiError } from '../../../core/http/api-error';
import type { Appointment, CreateAppointmentRequest, Slot } from '../models/scheduling';

/**
 * Acesso HTTP do domínio de agenda, seguindo `docs/openapi/scheduling.yaml` (#9).
 * Componentes injetam este serviço, nunca `HttpClient`.
 */
@Injectable({ providedIn: 'root' })
export class SchedulingApi {
  private readonly api = inject(ApiClient);

  /** Horários livres de um prestador num dia (`YYYY-MM-DD`), conforme o contrato. */
  listFreeSlots(
    providerId: string,
    date: string,
    durationMinutes: number,
  ): Observable<Slot[]> {
    return this.api.get<Slot[]>(`/providers/${providerId}/slots`, {
      params: { date, durationMinutes },
    });
  }

  /**
   * Cria um agendamento. `Idempotency-Key` é obrigatório no contrato: reenviar
   * a mesma chave devolve o mesmo agendamento em vez de criar outro.
   */
  createAppointment(
    body: CreateAppointmentRequest,
    idempotencyKey: string,
  ): Observable<Appointment> {
    return this.api.post<Appointment>('/appointments', body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  /** `true` quando o valor recebido é um erro da API já tipado. */
  isApiError(value: unknown): value is ApiError {
    return typeof value === 'object' && value !== null && 'status' in value;
  }
}
