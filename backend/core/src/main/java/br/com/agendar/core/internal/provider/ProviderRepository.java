package br.com.agendar.core.internal.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    /**
     * Prestadores ativos do tenant, em ordem de nome.
     */
    @Query("""
            SELECT p FROM Provider p
            WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL
            ORDER BY lower(p.name)
            """)
    List<Provider> findAtivosPorTenant(@Param("tenantId") UUID tenantId);

    /**
     * Prestador ativo vinculado ao usuário informado, no tenant.
     */
    @Query("""
            SELECT p FROM Provider p
            WHERE p.tenantId = :tenantId AND p.userId = :userId AND p.deletedAt IS NULL
            """)
    Optional<Provider> findAtivoPorUsuario(@Param("tenantId") UUID tenantId,
                                           @Param("userId") UUID userId);

    /**
     * Detalhe por id dentro do tenant, incluindo excluído: o agendamento antigo precisa
     * continuar resolvendo quem executou.
     */
    Optional<Provider> findByIdAndTenantId(UUID id, UUID tenantId);
}
