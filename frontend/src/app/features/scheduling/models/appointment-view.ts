/**
 * Espelha o `AppointmentView` do backend. Ver a issue #9 para o contrato completo.
 */
export interface AppointmentView {
  readonly id: string;
  readonly providerId: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly status: string;
}
