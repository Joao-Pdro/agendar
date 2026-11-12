package br.com.agendar.core.internal;

import br.com.agendar.core.api.ProviderFacade;
import br.com.agendar.core.internal.provider.Provider;
import br.com.agendar.core.internal.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ProviderFacadeImpl implements ProviderFacade {

    private final ProviderRepository prestadores;

    ProviderFacadeImpl(ProviderRepository prestadores) {
        this.prestadores = prestadores;
    }

    @Override
    public Optional<UUID> prestadorDoUsuario(UUID tenantId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return prestadores.findAtivoPorUsuario(tenantId, userId).map(Provider::getId);
    }

    @Override
    public boolean existeNoTenant(UUID tenantId, UUID providerId) {
        return prestadores.findByIdAndTenantId(providerId, tenantId)
                .filter(p -> !p.isExcluido())
                .isPresent();
    }
}
