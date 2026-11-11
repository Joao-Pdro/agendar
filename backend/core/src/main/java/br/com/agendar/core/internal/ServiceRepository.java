package br.com.agendar.core.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    /**
     * Lista só os serviços ativos do tenant. Excluído não aparece.
     */
    @Query("""
            SELECT s FROM Service s
            WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL
            ORDER BY lower(s.name)
            """)
    List<Service> findAtivosPorTenant(@Param("tenantId") UUID tenantId);

    /**
     * Detalhe por id, incluindo excluídos: agendamento antigo precisa continuar resolvendo o
     * serviço. O filtro de tenant é explícito para não vazar dado entre tenants.
     */
    Optional<Service> findByIdAndTenantId(UUID id, UUID tenantId);
}
