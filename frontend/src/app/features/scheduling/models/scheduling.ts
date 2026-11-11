/** Intervalo livre para agendamento. Espelha o schema `Slot` do contrato (#9). */
export interface Slot {
  readonly startsAt: string;
  readonly endsAt: string;
}

export type AppointmentStatus =
  | 'PENDENTE'
  | 'CONFIRMADO'
  | 'EM_EXECUCAO'
  | 'CONCLUIDO'
  | 'CANCELADO'
  | 'NO_SHOW';

/** Espelha o schema `Appointment` do contrato (#9). */
export interface Appointment {
  readonly id: string;
  readonly tenantId: string;
  readonly providerId: string;
  readonly customerId: string;
  readonly serviceId: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly status: AppointmentStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Espelha o schema `CreateAppointmentRequest` do contrato (#9). */
export interface CreateAppointmentRequest {
  readonly providerId: string;
  readonly customerId: string;
  readonly serviceId: string;
  readonly startsAt: string;
}
