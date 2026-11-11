package br.com.agendar.identity.api;

import java.util.UUID;

/**
 * Visão pública de um tenant, para os outros módulos.
 *
 * <p>É o único tipo de {@code identity} que pode ser importado de fora do módulo: entidade JPA e
 * repositório ficam em {@code internal}.
 */
public record TenantView(UUID id, String name, String slug, boolean customerSelfBooking) {
}
