package br.com.agendar.identity.internal;

import br.com.agendar.identity.api.IdentityFacade;
import br.com.agendar.identity.api.TenantView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class IdentityFacadeImpl implements IdentityFacade {

    private final TenantRepository tenants;

    IdentityFacadeImpl(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<TenantView> findTenant(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(t -> new TenantView(t.getId(), t.getName(), t.getSlug(), t.isCustomerSelfBooking()));
    }

    @Override
    public boolean isCustomerSelfBookingEnabled(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(Tenant::isCustomerSelfBooking)
                .orElse(false);
    }
}
