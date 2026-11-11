import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../../core/http/api-client';
import type { ApiError } from '../../../core/http/api-error';
import type { AppointmentView } from '../models/appointment-view';

/**
 * Acesso HTTP do domínio de agenda. Componentes injetam este serviço,
 * nunca `HttpClient`.
 */
@Injectable({ providedIn: 'root' })
export class SchedulingApi {
  private readonly api = inject(ApiClient);

  /** Agendamentos de um prestador no intervalo informado (ISO 8601, UTC). */
  listAppointments(
    providerId: string,
    from: string,
    to: string,
  ): Observable<AppointmentView[]> {
    return this.api.get<AppointmentView[]>('/scheduling/appointments', {
      params: { providerId, from, to },
    });
  }

  /** `true` quando o valor recebido é um erro da API já tipado. */
  isApiError(value: unknown): value is ApiError {
    return typeof value === 'object' && value !== null && 'status' in value;
  }
}
