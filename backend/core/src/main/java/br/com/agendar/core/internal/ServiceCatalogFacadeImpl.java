package br.com.agendar.core.internal;

import br.com.agendar.core.api.ServiceCatalogFacade;
import br.com.agendar.core.api.ServiceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ServiceCatalogFacadeImpl implements ServiceCatalogFacade {

    private final ServiceRepository servicos;

    ServiceCatalogFacadeImpl(ServiceRepository servicos) {
        this.servicos = servicos;
    }

    @Override
    public Optional<ServiceView> buscar(UUID tenantId, UUID serviceId) {
        return servicos.findByIdAndTenantId(serviceId, tenantId)
                .map(s -> new ServiceView(s.getId(), s.getTenantId(), s.getName(),
                        s.getDurationMinutes(), s.getBasePrice(), s.isExcluido()));
    }
}
