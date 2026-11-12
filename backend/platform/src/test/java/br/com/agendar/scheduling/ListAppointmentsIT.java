package br.com.agendar.scheduling;

import br.com.agendar.core.internal.Service;
import br.com.agendar.core.internal.ServiceRepository;
import br.com.agendar.identity.api.Role;
import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /providers/{providerId}/appointments} (issue #29).
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ListAppointmentsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    @Autowired private ServiceRepository servicos;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private br.com.agendar.core.internal.provider.ProviderRepository prestadoresProvider;
    @Autowired private br.com.agendar.scheduling.internal.availability.TenantTimezoneRepository fusos;
    @Autowired private br.com.agendar.scheduling.internal.availability.TenantAvailabilityRepository expedientes;

    private UUID tenantA;
    private UUID tenantB;
    private UUID prestadorA;
    private UUID outroPrestadorA;
    private UUID servicoA;
    private UUID cliente;
    private String tokenRecepcaoA;
    private String tokenPrestadorA;
    private String tokenRecepcaoB;

    private static final String SENHA = "SenhaForte123";
    private static final Instant INICIO = Instant.parse("2032-06-01T08:00:00Z");

    @BeforeEach
    void preparar() {
        prestadoresProvider.deleteAll();
        expedientes.deleteAll();
        fusos.deleteAll();
        servicos.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantA, "A", "a-" + tenantA));
        tenants.saveAndFlush(new Tenant(tenantB, "B", "b-" + tenantB));

        // Expediente do tenant (#39): sem isso o POST responde 422 FORA_DO_EXPEDIENTE.
        // 00:00-23:59 UTC cobre qualquer horário usado neste teste.
        fusos.saveAndFlush(new br.com.agendar.scheduling.internal.availability.TenantTimezone(tenantA, "UTC"));
        fusos.saveAndFlush(new br.com.agendar.scheduling.internal.availability.TenantTimezone(tenantB, "UTC"));
        for (var t : new UUID[]{tenantA, tenantB}) {
            for (var dia : java.time.DayOfWeek.values()) {
                expedientes.saveAndFlush(
                        new br.com.agendar.scheduling.internal.availability.TenantAvailability(
                                t, dia, java.time.LocalTime.MIN, java.time.LocalTime.of(23, 59)));
            }
        }

        prestadorA = UUID.randomUUID();
        outroPrestadorA = UUID.randomUUID();
        users.saveAndFlush(new AppUser(prestadorA, tenantA, "p1@a.com",
                passwordEncoder.encode(SENHA), "P1", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(outroPrestadorA, tenantA, "p2@a.com",
                passwordEncoder.encode(SENHA), "P2", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantA, "rec@a.com",
                passwordEncoder.encode(SENHA), "Rec", Role.RECEPCAO));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantB, "rec@b.com",
                passwordEncoder.encode(SENHA), "RecB", Role.RECEPCAO));

        // #40: id do prestador != id do usuário.
        prestadorA = criarPrestador(prestadorA, "P1");
        outroPrestadorA = criarPrestador(outroPrestadorA, "P2");

        servicoA = UUID.randomUUID();
        servicos.saveAndFlush(new Service(servicoA, tenantA, "Corte", 60, new BigDecimal("100.00")));
        cliente = UUID.randomUUID();

        tokenRecepcaoA = token(tenantA, "rec@a.com");
        tokenPrestadorA = token(tenantA, "p1@a.com");
        tokenRecepcaoB = token(tenantB, "rec@b.com");
    }

    @Test
    void listaAgendamentosDoPrestadorNoIntervalo() throws Exception {
        criarAgendamento(tokenRecepcaoA, prestadorA, INICIO);
        criarAgendamento(tokenRecepcaoA, prestadorA, INICIO.plusSeconds(7200));

        JsonNode lista = listar(prestadorA, INICIO.minusSeconds(3600), INICIO.plusSeconds(86400),
                tokenRecepcaoA, HttpStatus.OK);

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("startsAt").asText()).isEqualTo("2032-06-01T08:00:00Z");
        assertThat(lista.get(1).get("startsAt").asText()).isEqualTo("2032-06-01T10:00:00Z");
        assertThat(lista.get(0).get("status").asText()).isEqualTo("PENDENTE");
        for (String campo : new String[]{"id", "status", "startsAt", "endsAt", "customerId", "serviceId"}) {
            assertThat(lista.get(0).has(campo)).as("campo %s", campo).isTrue();
        }
    }

    @Test
    void naoDevolveCancelado() throws Exception {
        String id = criarAgendamento(tokenRecepcaoA, prestadorA, INICIO);
        criarAgendamento(tokenRecepcaoA, prestadorA, INICIO.plusSeconds(7200));

        // Cancela direto no banco: cancelamento por endpoint está fora do escopo desta issue.
        marcarCancelado(id);

        JsonNode lista = listar(prestadorA, INICIO.minusSeconds(3600), INICIO.plusSeconds(86400),
                tokenRecepcaoA, HttpStatus.OK);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("startsAt").asText()).isEqualTo("2032-06-01T10:00:00Z");
    }

    @Test
    void intervaloMaiorQue31DiasDevolve422() {
        ResponseEntity<String> resposta = rest.exchange(
                "/providers/" + prestadorA + "/appointments?from=" + INICIO + "&to=" + INICIO.plusSeconds(32L * 86400),
                HttpMethod.GET, new HttpEntity<>(headers(tokenRecepcaoA)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("RANGE_TOO_LONG");
    }

    @Test
    void prestadorNaoLeAgendaDeOutro() throws Exception {
        listar(outroPrestadorA, INICIO, INICIO.plusSeconds(86400), tokenPrestadorA, HttpStatus.FORBIDDEN);
        listar(prestadorA, INICIO, INICIO.plusSeconds(86400), tokenPrestadorA, HttpStatus.OK);
    }

    @Test
    void isolamentoDeTenant() throws Exception {
        criarAgendamento(tokenRecepcaoA, prestadorA, INICIO);

        // A recepção do tenant B consulta o mesmo prestadorId: o tenant do token não tem esse
        // prestador, então a lista vem vazia — nada do tenant A vaza.
        JsonNode listaB = listar(prestadorA, INICIO, INICIO.plusSeconds(86400),
                tokenRecepcaoB, HttpStatus.OK);

        assertThat(listaB).isEmpty();
    }

    @Test
    void fromDepoisDeToDevolve400() {
        ResponseEntity<String> resposta = rest.exchange(
                "/providers/" + prestadorA + "/appointments?from=" + INICIO + "&to=" + INICIO.minusSeconds(60),
                HttpMethod.GET, new HttpEntity<>(headers(tokenRecepcaoA)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- helpers

    private UUID criarPrestador(UUID userId, String nome) {
        var prestador = new br.com.agendar.core.internal.provider.Provider(
                UUID.randomUUID(), tenantA, nome, userId);
        prestadoresProvider.saveAndFlush(prestador);
        return prestador.getId();
    }

    private String criarAgendamento(String token, UUID providerId, Instant inicio) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("providerId", providerId.toString());
        corpo.put("customerId", cliente.toString());
        corpo.put("serviceId", servicoA.toString());
        corpo.put("startsAt", inicio.toString());

        HttpHeaders h = headers(token);
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> resposta = rest.postForEntity("/appointments",
                new HttpEntity<>(corpo, h), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return objectMapper.readTree(resposta.getBody()).get("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void marcarCancelado(String appointmentId) {
        // O endpoint de cancelamento está fora do escopo da #29, então o status é ajustado
        // direto no banco para provar o filtro da consulta.
        jdbc().update("UPDATE scheduling.appointment SET status = 'CANCELADO' WHERE id = ?::uuid",
                appointmentId);
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        if (jdbcTemplate == null) {
            jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        }
        return jdbcTemplate;
    }

    @Autowired
    private javax.sql.DataSource dataSource;

    private JsonNode listar(UUID providerId, Instant from, Instant to, String token, HttpStatus esperado)
            throws Exception {
        ResponseEntity<String> resposta = rest.exchange(
                "/providers/" + providerId + "/appointments?from=" + from + "&to=" + to,
                HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(esperado);
        return resposta.getStatusCode().is2xxSuccessful()
                ? objectMapper.readTree(resposta.getBody())
                : objectMapper.createArrayNode();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String token(UUID tenantId, String email) {
        ResponseEntity<String> resposta = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", tenantId.toString(),
                        "email", email, "password", SENHA), headers(null)), String.class);
        try {
            return objectMapper.readTree(resposta.getBody()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
