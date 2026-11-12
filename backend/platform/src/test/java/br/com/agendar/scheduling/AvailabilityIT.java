package br.com.agendar.scheduling;

import br.com.agendar.core.internal.Service;
import br.com.agendar.core.internal.ServiceRepository;
import br.com.agendar.identity.api.Role;
import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import br.com.agendar.scheduling.internal.availability.ProviderAvailability;
import br.com.agendar.scheduling.internal.availability.ProviderAvailabilityRepository;
import br.com.agendar.scheduling.internal.availability.TenantAvailability;
import br.com.agendar.scheduling.internal.availability.TenantAvailabilityRepository;
import br.com.agendar.scheduling.internal.availability.TenantTimezone;
import br.com.agendar.scheduling.internal.availability.TenantTimezoneRepository;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expediente por prestador com padrão do tenant (issue #39).
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "agendar.scheduling.slot.passo=PT60M")
@Testcontainers
class AvailabilityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    @Autowired private ServiceRepository servicos;
    @Autowired private TenantTimezoneRepository fusos;
    @Autowired private TenantAvailabilityRepository expedienteTenant;
    @Autowired private ProviderAvailabilityRepository expedientePrestador;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID prestadorId;
    private UUID outroPrestador;
    private UUID servicoId;
    private String tokenAdmin;
    private String tokenRecepcao;
    private String tokenPrestador;
    private static final String SENHA = "SenhaForte123";

    @BeforeEach
    void preparar() {
        expedientePrestador.deleteAll();
        expedienteTenant.deleteAll();
        fusos.deleteAll();
        servicos.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        tenantId = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantId, "Empresa", "av-" + tenantId));

        prestadorId = UUID.randomUUID();
        outroPrestador = UUID.randomUUID();
        users.saveAndFlush(new AppUser(prestadorId, tenantId, "p1@a.com",
                passwordEncoder.encode(SENHA), "P1", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(outroPrestador, tenantId, "p2@a.com",
                passwordEncoder.encode(SENHA), "P2", Role.PRESTADOR));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, "adm@a.com",
                passwordEncoder.encode(SENHA), "Adm", Role.ADMIN));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, "rec@a.com",
                passwordEncoder.encode(SENHA), "Rec", Role.RECEPCAO));

        servicoId = UUID.randomUUID();
        servicos.saveAndFlush(new Service(servicoId, tenantId, "Corte", 60, new BigDecimal("100.00")));

        fusos.saveAndFlush(new TenantTimezone(tenantId, "America/Sao_Paulo"));
        for (DayOfWeek dia : DayOfWeek.values()) {
            expedienteTenant.saveAndFlush(new TenantAvailability(tenantId, dia,
                    LocalTime.of(9, 0), LocalTime.of(11, 0)));
        }

        tokenAdmin = token("adm@a.com");
        tokenRecepcao = token("rec@a.com");
        tokenPrestador = token("p1@a.com");
    }

    @Test
    void semExpedienteProprioUsaOPadraoDoTenant() throws Exception {
        // 2030-03-11 é uma segunda-feira. São Paulo em março ainda está em UTC-3.
        JsonNode slots = slots(prestadorId, "2030-03-11", tokenRecepcao);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).get("startsAt").asText()).isEqualTo("2030-03-11T12:00:00Z");
        assertThat(slots.get(1).get("startsAt").asText()).isEqualTo("2030-03-11T13:00:00Z");
    }

    @Test
    void expedienteDoPrestadorSubstituiOPadraoDoTenant() {
        // Prestador só atende à tarde.
        expedientePrestador.saveAndFlush(new ProviderAvailability(UUID.randomUUID(), tenantId,
                prestadorId, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)));

        ResponseEntity<String> resposta = get("/providers/" + prestadorId
                + "/slots?date=2030-03-11&durationMinutes=60", tokenRecepcao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            JsonNode slots = objectMapper.readTree(resposta.getBody());
            assertThat(slots).hasSize(2);
            // 14:00 local = 17:00 UTC
            assertThat(slots.get(0).get("startsAt").asText()).isEqualTo("2030-03-11T17:00:00Z");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void prestadorComExpedienteProprioNaoUsaOPadraoDoTenantNosDiasQueNaoConfigurou() throws Exception {
        // A regra da issue: se o prestador tem linhas, elas valem integralmente — um dia sem
        // linha é dia sem atendimento, e não "cai no padrão do tenant".
        expedientePrestador.saveAndFlush(new ProviderAvailability(UUID.randomUUID(), tenantId,
                prestadorId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        // 2030-03-12 é terça: o tenant tem padrão nesse dia, o prestador não.
        assertThat(slots(prestadorId, "2030-03-12", tokenRecepcao)).isEmpty();
        // Segunda, que ele configurou, funciona.
        assertThat(slots(prestadorId, "2030-03-11", tokenRecepcao)).isNotEmpty();
    }

    @Test
    void gradeNaoSeDeslocaNaTrocaDeHorarioDeVerao() throws Exception {
        // São Paulo não tem mais horário de verão (desde 2019), então o fuso do tenant é trocado
        // para Europe/Lisbon, que muda de offset em 2030-03-31 (UTC+0 -> UTC+1).
        var tz = fusos.findById(tenantId).orElseThrow();
        tz.alterar("Europe/Lisbon");
        fusos.saveAndFlush(tz);

        JsonNode antes = slots(prestadorId, "2030-03-30", tokenRecepcao);
        JsonNode depois = slots(prestadorId, "2030-03-31", tokenRecepcao);

        assertThat(antes).isNotEmpty();
        assertThat(depois).isNotEmpty();

        // Antes: 09:00 local (UTC+0) = 09:00Z. Depois: 09:00 local (UTC+1) = 08:00Z.
        assertThat(antes.get(0).get("startsAt").asText()).isEqualTo("2030-03-30T09:00:00Z");
        assertThat(depois.get(0).get("startsAt").asText()).isEqualTo("2030-03-31T08:00:00Z");

        // O importante: a hora local do slot continua 09:00 nos dois dias.
        java.time.ZoneId lisboa = java.time.ZoneId.of("Europe/Lisbon");
        assertThat(java.time.Instant.parse(antes.get(0).get("startsAt").asText())
                .atZone(lisboa).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(java.time.Instant.parse(depois.get(0).get("startsAt").asText())
                .atZone(lisboa).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void adminConfiguraOPadraoDoTenantEAsConfiguracoesSaoLidas() throws Exception {
        ResponseEntity<String> resposta = put("/availability/tenant", tokenAdmin,
                Map.of("janelas", List.of(
                        Map.of("weekday", "MONDAY", "start", "08:00", "end", "12:00"),
                        Map.of("weekday", "TUESDAY", "start", "13:00", "end", "18:00"))));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = objectMapper.readTree(resposta.getBody());
        assertThat(corpo.get("origem").asText()).isEqualTo("tenant");
        assertThat(corpo.get("janelas")).hasSize(2);

        JsonNode lido = objectMapper.readTree(get("/availability/tenant", tokenAdmin).getBody());
        assertThat(lido.get("timezone").asText()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void recepcaoNaoAlteraOPadraoDoTenantMasConfiguraPrestador() {
        assertThat(put("/availability/tenant", tokenRecepcao, Map.of("janelas", List.of()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> doPrestador = put("/availability/providers/" + prestadorId,
                tokenRecepcao, Map.of("janelas", List.of(
                        Map.of("weekday", "WEDNESDAY", "start", "10:00", "end", "12:00"))));
        assertThat(doPrestador.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prestadorNaoConfiguraExpedienteDeOutro() {
        assertThat(put("/availability/providers/" + outroPrestador, tokenPrestador,
                Map.of("janelas", List.of()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void agendarForaDoExpedienteDevolve422() {
        // Expediente 09:00-11:00 local (12:00-14:00 UTC). 15:00 UTC está fora.
        ResponseEntity<String> resposta = agendar("2030-03-11T15:00:00Z", tokenRecepcao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("FORA_DO_EXPEDIENTE");
    }

    @Test
    void agendarDentroDoExpedientePassa() {
        assertThat(agendar("2030-03-11T12:00:00Z", tokenRecepcao).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void fusoInvalidoDevolve422() {
        ResponseEntity<String> resposta = put("/availability/tenant/timezone", tokenAdmin,
                Map.of("timezone", "Marte/Olympus"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("FUSO_INVALIDO");
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode slots(UUID providerId, String data, String token) throws Exception {
        ResponseEntity<String> resposta = get("/providers/" + providerId
                + "/slots?date=" + data + "&durationMinutes=60", token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(resposta.getBody());
    }

    private ResponseEntity<String> agendar(String inicio, String token) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("providerId", prestadorId.toString());
        corpo.put("customerId", UUID.randomUUID().toString());
        corpo.put("serviceId", servicoId.toString());
        corpo.put("startsAt", inicio);
        HttpHeaders h = headers(token);
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        return rest.postForEntity("/appointments", new HttpEntity<>(corpo, h), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
    }

    private ResponseEntity<String> put(String path, String token, Map<String, Object> corpo) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(corpo, headers(token)), String.class);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String token(String email) {
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
