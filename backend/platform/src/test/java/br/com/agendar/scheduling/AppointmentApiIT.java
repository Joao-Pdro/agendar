package br.com.agendar.scheduling;

import br.com.agendar.core.internal.Service;
import br.com.agendar.core.internal.ServiceRepository;
import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.api.Role;
import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato da agenda (#18): slots livres e criação de agendamento com idempotência.
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "agendar.scheduling.slot.passo=PT30M")
@Testcontainers
class AppointmentApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    @Autowired private ServiceRepository servicos;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private br.com.agendar.scheduling.internal.availability.TenantTimezoneRepository fusos;
    @Autowired private br.com.agendar.scheduling.internal.availability.TenantAvailabilityRepository expedientes;

    private UUID tenantId;
    private UUID prestadorId;
    private UUID outroPrestadorId;
    private UUID servicoId;
    private String tokenPrestador;
    private String tokenRecepcao;

    private static final String SENHA = "SenhaForte123";

    @BeforeEach
    void preparar() {
        servicos.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        fusos.deleteAll();
        expedientes.deleteAll();

        tenantId = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantId, "Empresa Agenda", "ag-" + tenantId));

        // Expediente padrão do tenant: 08:00-12:00 UTC, todo dia. Substitui o provisório da #18.
        fusos.saveAndFlush(new br.com.agendar.scheduling.internal.availability.TenantTimezone(tenantId, "UTC"));
        for (java.time.DayOfWeek dia : java.time.DayOfWeek.values()) {
            expedientes.saveAndFlush(new br.com.agendar.scheduling.internal.availability.TenantAvailability(
                    tenantId, dia, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0)));
        }

        prestadorId = UUID.randomUUID();
        outroPrestadorId = UUID.randomUUID();
        users.saveAndFlush(new AppUser(prestadorId, tenantId, "p1@a.com",
                passwordEncoder.encode(SENHA), "P1", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(outroPrestadorId, tenantId, "p2@a.com",
                passwordEncoder.encode(SENHA), "P2", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, "rec@a.com",
                passwordEncoder.encode(SENHA), "Rec", Role.RECEPCAO));

        servicoId = UUID.randomUUID();
        servicos.saveAndFlush(new Service(servicoId, tenantId, "Corte", 60, new BigDecimal("100.00")));

        tokenPrestador = token("p1@a.com");
        tokenRecepcao = token("rec@a.com");
    }

    @Test
    void slotsLivresSaoOsDoExpedienteDescontandoAgendamentos() throws Exception {
        // Dia futuro: 2030-03-10, expediente 08:00-12:00 UTC.
        ResponseEntity<String> resposta = get("/providers/" + prestadorId
                + "/slots?date=2030-03-10&durationMinutes=60", tokenRecepcao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode slots = objectMapper.readTree(resposta.getBody());
        // 08-09, 08:30-09:30, 09-10, 09:30-10:30, 10-11, 10:30-11:30, 11-12 → 7 slots, passo 30min
        assertThat(slots).hasSize(7);
        assertThat(slots.get(0).get("startsAt").asText()).isEqualTo("2030-03-10T08:00:00Z");
        assertThat(slots.get(0).get("endsAt").asText()).isEqualTo("2030-03-10T09:00:00Z");
        assertThat(slots.get(0).get("startsAt").asText()).contains("Z");
    }

    @Test
    void slotsDescontamAgendamentoExistente() throws Exception {
        Instant inicio = Instant.parse("2030-03-10T09:00:00Z");
        assertThat(criar(tokenRecepcao, prestadorId, inicio, UUID.randomUUID().toString())
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode slots = objectMapper.readTree(get("/providers/" + prestadorId
                + "/slots?date=2030-03-10&durationMinutes=60", tokenRecepcao).getBody());

        for (JsonNode slot : slots) {
            Instant s = Instant.parse(slot.get("startsAt").asText());
            Instant e = Instant.parse(slot.get("endsAt").asText());
            assertThat(s.isBefore(inicio.plus(1, ChronoUnit.HOURS)) && e.isAfter(inicio))
                    .as("slot %s conflita com o agendamento das 09:00", s)
                    .isFalse();
        }
    }

    @Test
    void prestadorVeSoAPropriaAgendaNosSlots() {
        assertThat(get("/providers/" + prestadorId + "/slots?date=2030-03-10&durationMinutes=60",
                tokenPrestador).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/providers/" + outroPrestadorId + "/slots?date=2030-03-10&durationMinutes=60",
                tokenPrestador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void criacaoDevolve201ComOsDados() throws Exception {
        Instant inicio = Instant.parse("2030-03-10T08:00:00Z");
        UUID customerId = UUID.randomUUID();

        // Mesmo customerId no pedido e na asserção: `criar` gera um novo a cada chamada.
        ResponseEntity<String> resposta = criarComCliente(tokenRecepcao, prestadorId, inicio,
                "chave-1", customerId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode corpo = objectMapper.readTree(resposta.getBody());
        assertThat(corpo.get("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(corpo.get("providerId").asText()).isEqualTo(prestadorId.toString());
        assertThat(corpo.get("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(corpo.get("startsAt").asText()).isEqualTo("2030-03-10T08:00:00Z");
        assertThat(corpo.get("endsAt").asText()).isEqualTo("2030-03-10T09:00:00Z");
        assertThat(corpo.get("status").asText()).isEqualTo("PENDENTE");
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void mesmaChaveMesmoCorpoDevolveOMesmoAgendamento() throws Exception {
        Instant inicio = Instant.parse("2030-03-10T08:00:00Z");
        UUID customerId = UUID.randomUUID();

        String primeiro = criarComCliente(tokenRecepcao, prestadorId, inicio, "chave-x", customerId).getBody();
        String segundo = criarComCliente(tokenRecepcao, prestadorId, inicio, "chave-x", customerId).getBody();

        assertThat(objectMapper.readTree(primeiro).get("id").asText())
                .isEqualTo(objectMapper.readTree(segundo).get("id").asText());

        // Não criou um segundo agendamento: com 08:00-09:00 ocupado, saem os candidatos que
        // conflitam com ele (08:00-09:00 e 08:30-09:30), sobrando 5 dos 7 slots do expediente.
        JsonNode slots = objectMapper.readTree(get("/providers/" + prestadorId
                + "/slots?date=2030-03-10&durationMinutes=60", tokenRecepcao).getBody());
        assertThat(slots).hasSize(5);
    }

    @Test
    void mesmaChaveComCorpoDiferenteDevolve422() {
        Instant inicio = Instant.parse("2030-03-10T08:00:00Z");

        assertThat(criarComCliente(tokenRecepcao, prestadorId, inicio, "chave-y", UUID.randomUUID())
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> outroCorpo = criarComCliente(tokenRecepcao, prestadorId,
                Instant.parse("2030-03-10T10:00:00Z"), "chave-y", UUID.randomUUID());

        assertThat(outroCorpo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(outroCorpo.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void conflitoDeHorarioDevolve409ComProblemDetail() {
        Instant inicio = Instant.parse("2030-03-10T08:00:00Z");

        assertThat(criar(tokenRecepcao, prestadorId, inicio, UUID.randomUUID().toString())
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflito = criar(tokenRecepcao, prestadorId,
                inicio.plus(30, ChronoUnit.MINUTES), UUID.randomUUID().toString());

        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflito.getBody()).contains("SLOT_CONFLICT");
        assertThat(conflito.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    void prestadorNaoAgendaParaOutroPrestador() {
        ResponseEntity<String> resposta = criar(tokenPrestador, outroPrestadorId,
                Instant.parse("2030-03-10T08:00:00Z"), UUID.randomUUID().toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void servicoInexistenteDevolve404() {
        ResponseEntity<String> resposta = rest.postForEntity("/appointments",
                new HttpEntity<>(Map.of("providerId", prestadorId.toString(),
                        "customerId", UUID.randomUUID().toString(),
                        "serviceId", UUID.randomUUID().toString(),
                        "startsAt", "2030-03-10T08:00:00Z"), headers(tokenRecepcao, "k-404")),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).contains("SERVICE_NOT_FOUND");
    }

    @Test
    void dataNoPassadoDevolve422() {
        ResponseEntity<String> resposta = criar(tokenRecepcao, prestadorId,
                Instant.parse("2020-01-01T08:00:00Z"), UUID.randomUUID().toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void semIdempotencyKeyDevolve400() {
        ResponseEntity<String> resposta = rest.postForEntity("/appointments",
                new HttpEntity<>(Map.of("providerId", prestadorId.toString(),
                        "customerId", UUID.randomUUID().toString(),
                        "serviceId", servicoId.toString(),
                        "startsAt", "2030-03-10T08:00:00Z"), headers(tokenRecepcao, null)),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<String> criar(String token, UUID providerId, Instant inicio, String chave) {
        return criarComCliente(token, providerId, inicio, chave, UUID.randomUUID());
    }

    private ResponseEntity<String> criarComCliente(String token, UUID providerId, Instant inicio,
                                                 String chave, UUID customerId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("providerId", providerId.toString());
        corpo.put("customerId", customerId.toString());
        corpo.put("serviceId", servicoId.toString());
        corpo.put("startsAt", inicio.toString());
        return rest.postForEntity("/appointments",
                new HttpEntity<>(corpo, headers(token, chave)), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token, null)), String.class);
    }

    private HttpHeaders headers(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private String token(String email) {
        AppUser user = users.findAll().stream()
                .filter(u -> u.getEmail().equals(email)).findFirst().orElseThrow();
        ResponseEntity<String> resposta = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", tenantId.toString(),
                        "email", email, "password", SENHA), headers(null, null)),
                String.class);
        try {
            return objectMapper.readTree(resposta.getBody()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
