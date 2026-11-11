package br.com.agendar.scheduling.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Agendamentos do prestador que ocupam a agenda no período, ignorando cancelados.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.prestadorId = :prestadorId
              AND a.status <> br.com.agendar.scheduling.internal.AppointmentStatus.CANCELADO
              AND a.startsAt < :fim
              AND a.endsAt > :inicio
            """)
    List<Appointment> ocupadosNoPeriodo(@Param("prestadorId") UUID prestadorId,
                                        @Param("inicio") Instant inicio,
                                        @Param("fim") Instant fim);

    Optional<Appointment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    /**
     * Agendamentos de um prestador no intervalo, para a grade da agenda.
     *
     * <p>O tenant entra no filtro de propósito: o id do prestador vem da URL e a checagem de
     * permissão não garante sozinho que o prestador é do mesmo tenant.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.tenantId = :tenantId
              AND a.prestadorId = :prestadorId
              AND a.status <> br.com.agendar.scheduling.internal.AppointmentStatus.CANCELADO
              AND a.startsAt < :fim
              AND a.endsAt > :inicio
            ORDER BY a.startsAt
            """)
    List<Appointment> doPrestadorNoPeriodo(@Param("tenantId") UUID tenantId,
                                           @Param("prestadorId") UUID prestadorId,
                                           @Param("inicio") Instant inicio,
                                           @Param("fim") Instant fim);
}
