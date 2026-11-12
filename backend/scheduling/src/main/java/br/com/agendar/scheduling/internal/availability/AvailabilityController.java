package br.com.agendar.scheduling.internal.availability;

import br.com.agendar.identity.api.AuthenticatedUser;
import br.com.agendar.identity.api.Role;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expediente semanal do tenant e do prestador (issue #39).
 *
 * <p>Regras de escrita: admin configura o padrão do tenant; admin e recepção configuram o
 * expediente de qualquer prestador. O prestador ainda não edita o próprio expediente porque o
 * vínculo usuário → prestador não existe (#40); quando existir, a checagem passa a aceitar o
 * dono.
 */
@RestController
@RequestMapping("/availability")
class AvailabilityController {

    private final AvailabilityConfigService expediente;

    AvailabilityController(AvailabilityConfigService expediente) {
        this.expediente = expediente;
    }

    /**
     * Expediente em hora local: {@code "08:00"} a {@code "18:00"}, dia ISO (1 = segunda).
     */
    public record JanelaRequest(String weekday, @NotBlank String start, @NotBlank String end) {
    }

    public record ExpedienteRequest(List<JanelaRequest> janelas) {
    }

    public record JanelaResponse(String weekday, String start, String end) {
    }

    public record ExpedienteResponse(String timezone, String origem, List<JanelaResponse> janelas) {
    }

    public record TimezoneRequest(@NotBlank String timezone) {
    }

    @GetMapping("/tenant")
    ResponseEntity<ExpedienteResponse> doTenant(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new ExpedienteResponse(
                expediente.fusoDoTenant(user.tenantId()).getId(),
                "tenant",
                paraResposta(expediente.expedienteDoTenant(user.tenantId()))));
    }

    @PutMapping("/tenant")
    ResponseEntity<?> definirDoTenant(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestBody ExpedienteRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (user.role() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN,
                    "SOMENTE_ADMIN", "Só o admin do tenant configura o expediente padrão"));
        }
        var janelas = converter(request.janelas());
        if (janelas.isErro()) {
            return ResponseEntity.unprocessableEntity().body(problema("EXPEDIENTE_INVALIDO", janelas.erro()));
        }
        expediente.definirExpedienteDoTenant(user.tenantId(), janelas.janelas());
        return ResponseEntity.ok(new ExpedienteResponse(
                expediente.fusoDoTenant(user.tenantId()).getId(), "tenant",
                paraResposta(expediente.expedienteDoTenant(user.tenantId()))));
    }

    @PutMapping("/tenant/timezone")
    ResponseEntity<?> definirFuso(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestBody TimezoneRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (user.role() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN,
                    "SOMENTE_ADMIN", "Só o admin do tenant altera o fuso"));
        }
        try {
            expediente.definirFuso(user.tenantId(), request.timezone());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(problema("FUSO_INVALIDO", e.getMessage()));
        }
        return ResponseEntity.ok(new ExpedienteResponse(
                expediente.fusoDoTenant(user.tenantId()).getId(), "tenant",
                paraResposta(expediente.expedienteDoTenant(user.tenantId()))));
    }

    @GetMapping("/providers/{providerId}")
    ResponseEntity<?> doPrestador(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable UUID providerId) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!podeConfigurar(user, providerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN,
                    "SEM_PERMISSAO", "Sem permissão para o expediente deste prestador"));
        }

        // Sem expediente próprio, a resposta mostra o padrão do tenant e indica a origem.
        Map<DayOfWeek, AvailabilityConfigService.Janela> proprias =
                expediente.expedienteDoPrestador(user.tenantId(), providerId);
        boolean usaPadrao = proprias.isEmpty();
        Map<DayOfWeek, AvailabilityConfigService.Janela> janelas =
                usaPadrao ? expediente.expedienteDoTenant(user.tenantId()) : proprias;

        return ResponseEntity.ok(new ExpedienteResponse(
                expediente.fusoDoTenant(user.tenantId()).getId(),
                usaPadrao ? "tenant" : "provider",
                paraResposta(janelas)));
    }

    @PutMapping("/providers/{providerId}")
    ResponseEntity<?> definirDoPrestador(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID providerId,
                                        @RequestBody ExpedienteRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!podeConfigurar(user, providerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN,
                    "SEM_PERMISSAO", "Sem permissão para o expediente deste prestador"));
        }
        var janelas = converter(request.janelas());
        if (janelas.isErro()) {
            return ResponseEntity.unprocessableEntity().body(problema("EXPEDIENTE_INVALIDO", janelas.erro()));
        }
        expediente.definirExpedienteDoPrestador(user.tenantId(), providerId, janelas.janelas());
        return ResponseEntity.ok(new ExpedienteResponse(
                expediente.fusoDoTenant(user.tenantId()).getId(), "provider",
                paraResposta(expediente.expedienteDoPrestador(user.tenantId(), providerId))));
    }

    /**
     * Admin e recepção configuram qualquer prestador do tenant. O prestador só o próprio, e
     * isso depende do vínculo usuário → prestador (#40).
     */
    private static boolean podeConfigurar(AuthenticatedUser user, UUID providerId) {
        if (user.role() == Role.ADMIN || user.role() == Role.RECEPCAO) {
            return true;
        }
        return user.role() == Role.PRESTADOR && user.userId().equals(providerId);
    }

    private static List<JanelaResponse> paraResposta(Map<DayOfWeek, AvailabilityConfigService.Janela> janelas) {
        return janelas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new JanelaResponse(
                        e.getKey().name(),
                        e.getValue().inicio().toString(),
                        e.getValue().fim().toString()))
                .toList();
    }

    private static Conversao converter(List<JanelaRequest> pedidos) {
        if (pedidos == null) {
            return new Conversao(List.of(), null);
        }
        List<AvailabilityConfigService.Janela> janelas = new ArrayList<>();
        for (JanelaRequest pedido : pedidos) {
            DayOfWeek dia;
            try {
                dia = DayOfWeek.valueOf(pedido.weekday());
            } catch (RuntimeException e) {
                return new Conversao(List.of(), "weekday inválido: " + pedido.weekday());
            }
            try {
                LocalTime inicio = LocalTime.parse(pedido.start());
                LocalTime fim = LocalTime.parse(pedido.end());
                janelas.add(new AvailabilityConfigService.Janela(dia, inicio, fim));
            } catch (DateTimeParseException e) {
                return new Conversao(List.of(), "horário inválido: " + pedido.start() + " - " + pedido.end());
            }
        }
        return new Conversao(janelas, null);
    }

    private record Conversao(List<AvailabilityConfigService.Janela> janelas, String erro) {
        boolean isErro() {
            return erro != null;
        }
    }

    private static ProblemDetail problema(String code, String detail) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    private static ProblemDetail problema(HttpStatus status, String code, String detail) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detail);
        problema.setTitle(status.getReasonPhrase());
        problema.setProperty("code", code);
        return problema;
    }
}
