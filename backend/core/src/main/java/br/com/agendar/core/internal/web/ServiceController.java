package br.com.agendar.core.internal.web;

import br.com.agendar.core.internal.Service;
import br.com.agendar.core.internal.ServiceRepository;
import br.com.agendar.identity.internal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo de serviços do tenant.
 *
 * <p>Isolamento de tenant é obrigatório em todas as operações: o tenant vem do token, nunca da
 * URL ou do corpo. Serviço de outro tenant responde 404, não 403 — nem a existência é revelada.
 */
@RestController
@RequestMapping("/services")
class ServiceController {

    private final ServiceRepository servicos;

    ServiceController(ServiceRepository servicos) {
        this.servicos = servicos;
    }

    public record CriarServiceRequest(
            @NotBlank String name,
            @Positive int durationMinutes,
            @NotNull @DecimalMin("0.00") BigDecimal basePrice) {
    }

    public record AlterarServiceRequest(
            String name,
            Integer durationMinutes,
            BigDecimal basePrice) {
    }

    public record ServiceResponse(
            UUID id,
            String name,
            int durationMinutes,
            BigDecimal basePrice,
            Instant deletedAt) {

        static ServiceResponse de(Service s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDurationMinutes(),
                    s.getBasePrice(), s.getDeletedAt());
        }
    }

    @GetMapping
    ResponseEntity<List<ServiceResponse>> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(servicos.findAtivosPorTenant(user.tenantId()).stream()
                .map(ServiceResponse::de)
                .toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<ServiceResponse> detalhe(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID id) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Excluído continua acessível aqui: agendamento antigo precisa resolver o serviço.
        return servicos.findByIdAndTenantId(id, user.tenantId())
                .map(ServiceResponse::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    @Transactional
    ResponseEntity<ServiceResponse> criar(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody CriarServiceRequest request) {
        if (!CatalogoPermissao.podeEscrever(user)) {
            return ResponseEntity.status(CatalogoPermissao.statusSeNaoPodeEscrever(user)).build();
        }

        Service servico = new Service(UUID.randomUUID(), user.tenantId(),
                request.name(), request.durationMinutes(), request.basePrice());
        servicos.saveAndFlush(servico);

        return ResponseEntity.created(URI.create("/services/" + servico.getId()))
                .body(ServiceResponse.de(servico));
    }

    @PatchMapping("/{id}")
    @Transactional
    ResponseEntity<ServiceResponse> alterar(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID id,
                                            @RequestBody AlterarServiceRequest request) {
        if (!CatalogoPermissao.podeEscrever(user)) {
            return ResponseEntity.status(CatalogoPermissao.statusSeNaoPodeEscrever(user)).build();
        }

        var encontrado = servicos.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Service servico = encontrado.get();
        if (request.name() != null) {
            servico.renomear(request.name());
        }
        if (request.durationMinutes() != null) {
            servico.alterarDuracao(request.durationMinutes());
        }
        if (request.basePrice() != null) {
            servico.alterarPreco(request.basePrice());
        }
        servicos.saveAndFlush(servico);

        return ResponseEntity.ok(ServiceResponse.de(servico));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable UUID id) {
        if (!CatalogoPermissao.podeEscrever(user)) {
            return ResponseEntity.status(CatalogoPermissao.statusSeNaoPodeEscrever(user)).build();
        }

        var encontrado = servicos.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        encontrado.get().excluir(Instant.now());
        servicos.saveAndFlush(encontrado.get());

        return ResponseEntity.noContent().build();
    }
}
