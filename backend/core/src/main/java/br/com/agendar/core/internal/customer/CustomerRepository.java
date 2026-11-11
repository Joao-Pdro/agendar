package br.com.agendar.core.internal.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Clientes ativos do tenant, todos.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL
            ORDER BY lower(c.name)
            """)
    List<Customer> findAtivosPorTenant(@Param("tenantId") UUID tenantId);

    /**
     * Clientes ativos do tenant filtrados por trecho do nome ou do documento.
     *
     * <p>Consulta separada da listagem de propósito: um parâmetro nulo em
     * {@code lower(:termo)} faz o PostgreSQL inferir {@code bytea} e falhar com
     * {@code function lower(bytea) does not exist}. Duas consultas evitam o problema sem cast.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND (lower(c.name) LIKE lower(concat('%', :termo, '%'))
                   OR c.document LIKE concat('%', :termo, '%'))
            ORDER BY lower(c.name)
            """)
    List<Customer> buscarAtivos(@Param("tenantId") UUID tenantId, @Param("termo") String termo);

    /**
     * Detalhe por id dentro do tenant, incluindo excluído (agendamento antigo).
     */
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Cliente ativo com o mesmo documento, para detectar duplicata antes do índice único.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId AND c.document = :document AND c.deletedAt IS NULL
            """)
    Optional<Customer> findAtivoPorDocumento(@Param("tenantId") UUID tenantId,
                                             @Param("document") String document);
}
