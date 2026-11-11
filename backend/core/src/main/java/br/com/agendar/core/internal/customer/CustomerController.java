package br.com.agendar.core.internal.customer;

import br.com.agendar.identity.api.AuthenticatedUser;
import br.com.agendar.identity.api.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro de clientes.
 *
 * <p>Regras da issue #31: admin e recepção escrevem; prestador só lê. Cliente de outro tenant
 * responde 404.
 */
@RestController
@RequestMapping("/customers")
class CustomerController {

    private final CustomerRepository clientes;

    CustomerController(CustomerRepository clientes) {
        this.clientes = clientes;
    }

    public record CriarCustomerRequest(
            @NotBlank String name,
            @NotBlank String document,
            String phone,
            String email) {
    }

    public record AlterarCustomerRequest(String name, String phone, String email) {
    }

    public record CustomerResponse(
            UUID id,
            String name,
            String document,
            String phone,
            String email,
            Instant deletedAt) {

        static CustomerResponse de(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getDocument(), c.getPhone(),
                    c.getEmail(), c.getDeletedAt());
        }
    }

    @GetMapping
    ResponseEntity<List<CustomerResponse>> listar(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam(required = false) String q) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Customer> encontrados = (q == null || q.isBlank())
                ? clientes.findAtivosPorTenant(user.tenantId())
                : clientes.buscarAtivos(user.tenantId(), q.trim());
        return ResponseEntity.ok(encontrados.stream()
                .map(CustomerResponse::de)
                .toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<CustomerResponse> detalhe(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID id) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return clientes.findByIdAndTenantId(id, user.tenantId())
                .map(CustomerResponse::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    @Transactional
    ResponseEntity<?> criar(@AuthenticationPrincipal AuthenticatedUser user,
                           @Valid @RequestBody CriarCustomerRequest request) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }
        if (!Documento.valido(request.document())) {
            return ResponseEntity.unprocessableEntity().body(problema(
                    "DOCUMENTO_INVALIDO", "Documento inválido: " + request.document()));
        }

        String documento = Documento.normalizar(request.document());
        if (clientes.findAtivoPorDocumento(user.tenantId(), documento).isPresent()) {
            ProblemDetail duplicado = problema("DOCUMENTO_DUPLICADO",
                    "Já existe cliente com esse documento neste tenant");
            duplicado.setStatus(HttpStatus.CONFLICT);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(duplicado);
        }

        Customer cliente = new Customer(UUID.randomUUID(), user.tenantId(), request.name(),
                documento, request.phone(), request.email());
        clientes.saveAndFlush(cliente);

        return ResponseEntity.created(URI.create("/customers/" + cliente.getId()))
                .body(CustomerResponse.de(cliente));
    }

    @PatchMapping("/{id}")
    @Transactional
    ResponseEntity<?> alterar(@AuthenticationPrincipal AuthenticatedUser user,
                             @PathVariable UUID id,
                             @RequestBody AlterarCustomerRequest request) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }

        Optional<Customer> encontrado = clientes.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Customer cliente = encontrado.get();
        if (request.name() != null) {
            cliente.renomear(request.name());
        }
        cliente.alterarContato(request.phone(), request.email());
        clientes.saveAndFlush(cliente);

        return ResponseEntity.ok(CustomerResponse.de(cliente));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable UUID id) {
        if (!podeEscrever(user)) {
            return ResponseEntity.status(statusSeNaoPodeEscrever(user)).build();
        }

        Optional<Customer> encontrado = clientes.findByIdAndTenantId(id, user.tenantId());
        if (encontrado.isEmpty() || encontrado.get().isExcluido()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        encontrado.get().excluir(Instant.now());
        clientes.saveAndFlush(encontrado.get());

        return ResponseEntity.noContent().build();
    }

    /**
     * Documento inválido que escapar da checagem do controller (ex.: chamada de domínio).
     */
    @ExceptionHandler(Customer.DocumentoInvalidoException.class)
    ResponseEntity<ProblemDetail> documentoInvalido(Customer.DocumentoInvalidoException e) {
        return ResponseEntity.unprocessableEntity().body(problema("DOCUMENTO_INVALIDO", e.getMessage()));
    }

    private static boolean podeEscrever(AuthenticatedUser user) {
        return user != null && (user.role() == Role.ADMIN || user.role() == Role.RECEPCAO);
    }

    private static HttpStatus statusSeNaoPodeEscrever(AuthenticatedUser user) {
        return user == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
    }

    private static ProblemDetail problema(String code, String detail) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problema.setTitle("Requisição inválida");
        problema.setProperty("code", code);
        return problema;
    }
}
