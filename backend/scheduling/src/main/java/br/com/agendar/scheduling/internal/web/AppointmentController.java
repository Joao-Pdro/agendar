package br.com.agendar.scheduling.internal.web;

import br.com.agendar.core.api.ServiceView;
import br.com.agendar.identity.internal.security.AuthenticatedUser;
import br.com.agendar.scheduling.api.SchedulingFacade;
import br.com.agendar.scheduling.internal.AvailabilityService;
import br.com.agendar.scheduling.internal.SlotCalculator;
import br.com.agendar.scheduling.internal.dependencias.ServicoConsulta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoints do contrato {@code docs/openapi/scheduling.yaml}.
 *
 * <p>Regras de acesso da #5: prestador só na própria agenda; recepção e admin em qualquer
 * prestador do tenant.
 */
@RestController
@RequestMapping("/")
class AppointmentController {

    private final AvailabilityService availability;
    private final SchedulingFacade scheduling;
    private final ServicoConsulta servicos;
    private final ZoneId fuso;

    AppointmentController(AvailabilityService availability, SchedulingFacade scheduling,
                          ServicoConsulta servicos,
                          @Value("${agendar.scheduling.fuso:UTC}") String fuso) {
        this.availability = availability;
        this.scheduling = scheduling;
        this.servicos = servicos;
        this.fuso = ZoneId.of(fuso);
    }

    public record SlotResponse(Instant startsAt, Instant endsAt) {
    }

    public record CriarAgendamentoRequest(
            @NotNull UUID providerId,
            @NotNull UUID customerId,
            @NotNull UUID serviceId,
            @NotNull Instant startsAt) {
    }

    public record AppointmentResponse(
            UUID id,
            UUID tenantId,
            UUID providerId,
            UUID customerId,
            UUID serviceId,
            Instant startsAt,
            Instant endsAt,
            String status) {
    }

    @GetMapping("/providers/{providerId}/slots")
    ResponseEntity<?> slots(@AuthenticationPrincipal AuthenticatedUser user,
                            @PathVariable UUID providerId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam @Positive int durationMinutes) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!user.canAccessProvider(providerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<SlotResponse> slots = availability
                .slotsLivres(providerId, date, fuso, Duration.ofMinutes(durationMinutes)).stream()
                .map(s -> new SlotResponse(s.startsAt(), s.endsAt()))
                .toList();

        return ResponseEntity.ok(slots);
    }

    @PostMapping("/appointments")
    ResponseEntity<?> criar(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                            @Valid @RequestBody CriarAgendamentoRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!user.canAccessProvider(request.providerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // O serviço define a duração; serviço de outro tenant ou inexistente é 404.
        Optional<ServiceView> servico = servicos.buscar(user.tenantId(), request.serviceId());
        if (servico.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema(HttpStatus.NOT_FOUND,
                    "SERVICE_NOT_FOUND", "Serviço não encontrado"));
        }

        if (request.startsAt().isBefore(Instant.now())) {
            return ResponseEntity.unprocessableEntity().body(problema(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR", "startsAt deve ser uma data futura"));
        }

        var comando = new SchedulingFacade.AgendarCommand(user.tenantId(), request.providerId(),
                request.customerId(), request.serviceId(), request.startsAt(),
                servico.get().durationMinutes(), idempotencyKey);

        try {
            var criado = scheduling.agendar(comando);
            return ResponseEntity.created(URI.create("/appointments/" + criado.id()))
                    .body(new AppointmentResponse(criado.id(), criado.tenantId(), criado.providerId(),
                            criado.customerId(), criado.serviceId(), criado.startsAt(),
                            criado.endsAt(), criado.status()));
        } catch (SchedulingFacade.SlotConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problema(HttpStatus.CONFLICT,
                    "SLOT_CONFLICT", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getClass().getSimpleName().equals("ChaveReutilizadaException")) {
                return ResponseEntity.unprocessableEntity().body(problema(
                        HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", e.getMessage()));
            }
            throw e;
        }
    }

    private static ProblemDetail problema(HttpStatus status, String code, String detail) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detail);
        problema.setProperty("code", code);
        problema.setTitle(status.getReasonPhrase());
        return problema;
    }
}
