package br.com.agendar.core.internal.provider;

import br.com.agendar.identity.api.AuthenticatedUser;
import br.com.agendar.identity.api.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro de prestadores.
 *
 * <p>Regras da issue #30: admin escreve; recepção lê todos; prestador lê só o próprio registro.
 * Prestador de outro tenant responde 404, nunca 403 — nem a existência é revelada.
 */
@RestController
@RequestMapping("/providers")
class ProviderController {

    private final ProviderRepository prestadores;

    ProviderController(ProviderRepository prestadores) {
        this.prestadores = prestadores;
    }

    public record CriarProviderRequest(@NotBlank String name, UUID userId) {
    }

    public record AlterarProviderRequest(String name, UUID userId) {
    }

    public record ProviderResponse(UUID id, String name, UUID userId, Instant deletedAt) {

        static ProviderResponse de(Provider p) {
            return new ProviderResponse(p.getId(), p.getName(), p.getUserId(), p.getDeletedAt());
        }
    }

    @GetMapping
    ResponseEntity<List<ProviderResponse>> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!podeLerTodos(user)) {
            // Prestador vê só a si mesmo na lista: o vínculo é pelo usuário do token.
            return prestadores.findAtivoPorUsuario(user.tenantId(), user.userId())
                    .map(ProviderResponse::de)
                    .map(List::of)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.ok(List.of()));
        }
        return ResponseEntity.ok(prestadores.findAtivosPorTenant(user.tenantId()).stream()
                .map(ProviderResponse::de)
                .toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<ProviderResponse> detalhe(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID id) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Provider> encontrado = prestadores.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // O prestador só lê o próprio registro, e o vínculo é pelo usuário do token — o id do
        // prestador não é o id do usuário.
        if (!podeLerTodos(user) && !ehODono(user, encontrado.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ProviderResponse.de(encontrado.get()));
    }

    @PostMapping
    @Transactional
    ResponseEntity<ProviderResponse> criar(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody CriarProviderRequest request) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }

        Provider prestador = new Provider(UUID.randomUUID(), user.tenantId(),
                request.name(), request.userId());
        prestadores.saveAndFlush(prestador);

        return ResponseEntity.created(URI.create("/providers/" + prestador.getId()))
                .body(ProviderResponse.de(prestador));
    }

    @PatchMapping("/{id}")
    @Transactional
    ResponseEntity<ProviderResponse> alterar(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID id,
                                            @RequestBody AlterarProviderRequest request) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }

        var encontrado = prestadores.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Provider prestador = encontrado.get();
        if (request.name() != null) {
            prestador.renomear(request.name());
        }
        if (request.userId() != null) {
            prestador.vincularUsuario(request.userId());
        }
        prestadores.saveAndFlush(prestador);

        return ResponseEntity.ok(ProviderResponse.de(prestador));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable UUID id) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }

        var encontrado = prestadores.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        encontrado.get().excluir(Instant.now());
        prestadores.saveAndFlush(encontrado.get());

        return ResponseEntity.noContent().build();
    }

    private static boolean ehODono(AuthenticatedUser user, Provider prestador) {
        return user.userId().equals(prestador.getUserId());
    }

    private static boolean podeEscrever(AuthenticatedUser user) {
        return user != null && user.role() == Role.ADMIN;
    }

    private static boolean podeLerTodos(AuthenticatedUser user) {
        return user.role() == Role.ADMIN || user.role() == Role.RECEPCAO;
    }

    private static HttpStatus statusSeNaoPodeEscrever(AuthenticatedUser user) {
        return user == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
    }
}
