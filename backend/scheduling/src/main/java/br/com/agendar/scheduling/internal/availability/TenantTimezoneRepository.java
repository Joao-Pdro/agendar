package br.com.agendar.scheduling.internal.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantTimezoneRepository extends JpaRepository<TenantTimezone, UUID> {
}
