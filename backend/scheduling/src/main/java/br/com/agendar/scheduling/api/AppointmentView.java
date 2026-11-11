package br.com.agendar.scheduling.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Visão pública de um agendamento, usada pelos outros módulos.
 *
 * <p>Sem exposição de entidade JPA: quem está fora do módulo nunca vê a classe interna.
 */
public record AppointmentView(
        UUID id,
        UUID tenantId,
        UUID providerId,
        UUID customerId,
        UUID serviceId,
        Instant startsAt,
        Instant endsAt,
        String status) {
}
