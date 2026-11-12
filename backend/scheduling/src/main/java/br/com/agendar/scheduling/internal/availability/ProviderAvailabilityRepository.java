package br.com.agendar.scheduling.internal.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProviderAvailabilityRepository extends JpaRepository<ProviderAvailability, UUID> {

    List<ProviderAvailability> findAllByProviderIdAndTenantId(UUID providerId, UUID tenantId);

    void deleteAllByProviderIdAndTenantId(UUID providerId, UUID tenantId);
}
