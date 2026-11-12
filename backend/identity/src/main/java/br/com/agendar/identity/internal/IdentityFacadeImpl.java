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
    private final AppUserRepository users;

    IdentityFacadeImpl(TenantRepository tenants, AppUserRepository users) {
        this.tenants = tenants;
        this.users = users;
    }

    @Override
    public Optional<TenantView> findTenant(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(t -> new TenantView(t.getId(), t.getName(), t.getSlug(), t.isCustomerSelfBooking()));
    }

    @Override
    public boolean usuarioPertenceAoTenant(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        return users.findById(userId)
                .filter(AppUser::isActivo)
                .map(u -> tenantId.equals(u.getTenantId()))
                .orElse(false);
    }

    @Override
    public boolean isCustomerSelfBookingEnabled(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(Tenant::isCustomerSelfBooking)
                .orElse(false);
    }
}
