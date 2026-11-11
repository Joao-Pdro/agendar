package br.com.agendar.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Busca por e-mail dentro do tenant. O tenant é obrigatório na busca porque o mesmo e-mail
     * pode existir em tenants diferentes.
     */
    Optional<AppUser> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    Optional<AppUser> findByTenantIdIsNullAndEmailIgnoreCase(String email);

    List<AppUser> findByTenantId(UUID tenantId);
}
