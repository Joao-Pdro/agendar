package br.com.agendar.scheduling.internal.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantAvailabilityRepository extends JpaRepository<TenantAvailability, TenantAvailability.Key> {

    List<TenantAvailability> findAllByTenantId(UUID tenantId);

    void deleteAllByTenantId(UUID tenantId);
}
