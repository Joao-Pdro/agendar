package br.com.agendar.core.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Visão pública de um serviço do catálogo.
 */
public record ServiceView(
        UUID id,
        UUID tenantId,
        String name,
        int durationMinutes,
        BigDecimal basePrice,
        boolean excluido) {
}
