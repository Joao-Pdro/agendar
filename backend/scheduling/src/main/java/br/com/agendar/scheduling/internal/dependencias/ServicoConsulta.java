package br.com.agendar.scheduling.internal.dependencias;

import br.com.agendar.core.api.ServiceCatalogFacade;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador do {@code scheduling} para a API pública do {@code core}.
 *
 * <p>O {@code scheduling} não importa nada de {@code core.internal} (regra do ArchUnit da #22);
 * só a porta {@link ServiceCatalogFacade} do pacote {@code api}.
 */
@Component
public class ServicoConsulta {

    private final ServiceCatalogFacade catalogo;

    public ServicoConsulta(ServiceCatalogFacade catalogo) {
        this.catalogo = catalogo;
    }

    public Optional<br.com.agendar.core.api.ServiceView> buscar(UUID tenantId, UUID serviceId) {
        return catalogo.buscar(tenantId, serviceId);
    }
}
