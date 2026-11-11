package br.com.agendar.core.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do módulo {@code core} para os demais módulos.
 *
 * <p>A busca inclui serviços com soft delete: um agendamento antigo continua precisando resolver
 * a duração do serviço que foi executado.
 */
public interface ServiceCatalogFacade {

    Optional<ServiceView> buscar(UUID tenantId, UUID serviceId);
}
