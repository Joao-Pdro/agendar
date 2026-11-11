import type { Appointment } from '../models/scheduling';

/**
 * Dados de exemplo que a grade desenha enquanto não existem:
 * - o `GET /appointments` por prestador e período (issue a abrir pelo planner);
 * - o catálogo de serviços (#19);
 * - o cadastro de clientes.
 *
 * Nada disso é contrato. Só entra na tela com `environment.useMockAgenda = true`.
 */
export const MOCK_SERVICE = {
  id: 'c9d8e7f6-a5b4-4c3d-9e2f-1a0b9c8d7e6f',
  name: 'Serviço de exemplo',
} as const;

export const MOCK_CUSTOMER_ID = 'b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e';

const TODAY = new Date();
const iso = (dayOffset: number, hour: number): string => {
  const date = new Date(TODAY);
  date.setDate(date.getDate() + dayOffset);
  date.setHours(hour, 0, 0, 0);
  return date.toISOString();
};

/** Agendamentos de exemplo, relativos ao dia de hoje para a grade sempre ter conteúdo. */
export const MOCK_APPOINTMENTS: Appointment[] = [
  {
    id: 'mock-1',
    tenantId: 'mock-tenant',
    providerId: 'mock-provider',
    customerId: MOCK_CUSTOMER_ID,
    serviceId: MOCK_SERVICE.id,
    startsAt: iso(0, 9),
    endsAt: iso(0, 10),
    status: 'CONFIRMADO',
    createdAt: iso(0, 8),
    updatedAt: iso(0, 8),
  },
  {
    id: 'mock-2',
    tenantId: 'mock-tenant',
    providerId: 'mock-provider',
    customerId: MOCK_CUSTOMER_ID,
    serviceId: MOCK_SERVICE.id,
    startsAt: iso(0, 14),
    endsAt: iso(0, 15),
    status: 'PENDENTE',
    createdAt: iso(0, 8),
    updatedAt: iso(0, 8),
  },
  {
    id: 'mock-3',
    tenantId: 'mock-tenant',
    providerId: 'mock-provider',
    customerId: MOCK_CUSTOMER_ID,
    serviceId: MOCK_SERVICE.id,
    startsAt: iso(1, 11),
    endsAt: iso(1, 12),
    status: 'CONFIRMADO',
    createdAt: iso(0, 8),
    updatedAt: iso(0, 8),
  },
];
