package br.com.agendar.identity.internal.web;

import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import br.com.agendar.identity.internal.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Configurações do tenant.
 *
 * <p>Só o admin do tenant altera a flag {@code customer_self_booking}. Prestador e recepção
 * recebem 403.
 */
@RestController
@RequestMapping("/tenant")
class TenantSettingsController {

    private final TenantRepository tenants;

    TenantSettingsController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public record TenantSettingsRequest(@NotNull Boolean customerSelfBooking) {
    }

    public record TenantSettingsResponse(UUID id, String slug, boolean customerSelfBooking) {
    }

    @PatchMapping("/settings")
    @Transactional
    ResponseEntity<TenantSettingsResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestBody TenantSettingsRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!user.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Tenant> encontrado = tenants.findById(user.tenantId());
        if (encontrado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Tenant tenant = encontrado.get();
        tenant.setCustomerSelfBooking(request.customerSelfBooking());
        tenants.saveAndFlush(tenant);

        return ResponseEntity.ok(
                new TenantSettingsResponse(tenant.getId(), tenant.getSlug(), tenant.isCustomerSelfBooking()));
    }
}
