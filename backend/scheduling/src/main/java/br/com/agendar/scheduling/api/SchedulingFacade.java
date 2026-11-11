package br.com.agendar.scheduling.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta pública do módulo {@code scheduling}.
 */
public interface SchedulingFacade {

    /**
     * Cria um agendamento. A mesma chave de idempotência devolve o agendamento já criado.
     *
     * @throws SlotConflictException quando o horário já está ocupado para o prestador
     */
    AppointmentView agendar(AgendarCommand comando);

    record AgendarCommand(
            UUID tenantId,
            UUID providerId,
            UUID customerId,
            UUID serviceId,
            Instant startsAt,
            int durationMinutes,
            String idempotencyKey) {
    }

    /**
     * O horário pedido conflita com outro agendamento do mesmo prestador.
     */
    class SlotConflictException extends RuntimeException {
        public SlotConflictException(String mensagem) {
            super(mensagem);
        }
    }
}
