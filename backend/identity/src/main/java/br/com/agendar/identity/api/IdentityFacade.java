package br.com.agendar.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do módulo {@code identity} para os demais módulos.
 *
 * <p>Quem está fora do módulo usa esta interface; a implementação vive em {@code internal}.
 */
public interface IdentityFacade {

    Optional<TenantView> findTenant(UUID tenantId);

    /**
     * Indica se o cliente final pode marcar horário sozinho neste tenant.
     * Tenant inexistente responde {@code false}.
     */
    boolean isCustomerSelfBookingEnabled(UUID tenantId);
}
