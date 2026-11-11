package br.com.agendar.core;

import br.com.agendar.core.internal.ServiceRepository;
import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.internal.Role;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catálogo de serviços (#19), exercitado por HTTP.
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ServiceCatalogIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ServiceRepository servicos;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantA;
    private UUID tenantB;
    private String adminA;
    private String recepcaoA;
    private String prestadorA;
    private String adminB;
    private static final String SENHA = "SenhaForte123";

    @BeforeEach
    void preparar() {
        servicos.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        tenantA = criarTenant("Empresa A", "core-a-" + UUID.randomUUID());
        tenantB = criarTenant("Empresa B", "core-b-" + UUID.randomUUID());

        adminA = criarUsuario(tenantA, "admin-a@t.com", Role.ADMIN);
        recepcaoA = criarUsuario(tenantA, "recepcao-a@t.com", Role.RECEPCAO);
        prestadorA = criarUsuario(tenantA, "prestador-a@t.com", Role.PRESTADOR);
        adminB = criarUsuario(tenantB, "admin-b@t.com", Role.ADMIN);
    }

    @Test
    void adminCriaEServicoNasceAtivo() throws Exception {
        ResponseEntity<String> resposta = criar(adminA, "Corte de grama", 60, "150.00");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode corpo = objectMapper.readTree(resposta.getBody());
        assertThat(corpo.get("name").asText()).isEqualTo("Corte de grama");
        assertThat(corpo.get("durationMinutes").asInt()).isEqualTo(60);
        assertThat(corpo.get("basePrice").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(corpo.get("deletedAt").isNull()).isTrue();
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void recepcaoEPrestadorNaoEscrevem() {
        assertThat(criar(recepcaoA, "X", 30, "10.00").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(criar(prestadorA, "X", 30, "10.00").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recepcaoEPrestadorLeem() throws Exception {
        String id = criar(adminA, "Pintura", 120, "500.00").getBody();
        String serviceId = objectMapper.readTree(id).get("id").asText();

        assertThat(get("/services", recepcaoA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/services", prestadorA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/services/" + serviceId, prestadorA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void servicoDeUmTenantNaoApareceParaOutro() throws Exception {
        String serviceIdA = objectMapper.readTree(criar(adminA, "Elétrica", 90, "300.00").getBody())
                .get("id").asText();
        criar(adminB, "Elétrica", 90, "300.00");

        JsonNode listaB = objectMapper.readTree(get("/services", adminB).getBody());
        assertThat(listaB).hasSize(1);

        // O id do tenant A não é visível nem pelo detalhe.
        assertThat(get("/services/" + serviceIdA, adminB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void softDeleteTiraDAListaMasMantemODetalhe() throws Exception {
        String serviceId = objectMapper.readTree(criar(adminA, "Montagem", 45, "80.00").getBody())
                .get("id").asText();

        ResponseEntity<String> exclusao = rest.exchange("/services/" + serviceId, HttpMethod.DELETE,
                new HttpEntity<>(headers(adminA)), String.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode lista = objectMapper.readTree(get("/services", adminA).getBody());
        assertThat(lista).isEmpty();

        // Agendamento antigo continua resolvendo o serviço.
        ResponseEntity<String> detalhe = get("/services/" + serviceId, adminA);
        assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(detalhe.getBody()).get("deletedAt").isNull()).isFalse();
    }

    @Test
    void patchAlteraEEditarExcluidoResponde404() throws Exception {
        String serviceId = objectMapper.readTree(criar(adminA, "Revisão", 30, "100.00").getBody())
                .get("id").asText();

        ResponseEntity<String> alterado = rest.exchange("/services/" + serviceId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Revisão completa", "durationMinutes", 50), headers(adminA)),
                String.class);
        assertThat(alterado.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = objectMapper.readTree(alterado.getBody());
        assertThat(corpo.get("name").asText()).isEqualTo("Revisão completa");
        assertThat(corpo.get("durationMinutes").asInt()).isEqualTo(50);
        assertThat(corpo.get("basePrice").decimalValue()).isEqualByComparingTo("100.00");

        rest.exchange("/services/" + serviceId, HttpMethod.DELETE,
                new HttpEntity<>(headers(adminA)), String.class);

        ResponseEntity<String> depoisDeExcluido = rest.exchange("/services/" + serviceId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Outro"), headers(adminA)), String.class);
        assertThat(depoisDeExcluido.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requisicaoSemTokenResponde401() {
        assertThat(rest.getForEntity("/services", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- helpers

    private UUID criarTenant(String nome, String slug) {
        UUID id = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(id, nome, slug));
        return id;
    }

    private String criarUsuario(UUID tenantId, String email, Role role) {
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, email,
                passwordEncoder.encode(SENHA), email, role));
        return email;
    }

    private ResponseEntity<String> criar(String email, String nome, int duracao, String preco) {
        return rest.postForEntity("/services",
                new HttpEntity<>(Map.of("name", nome, "durationMinutes", duracao, "basePrice", preco),
                        headers(email)),
                String.class);
    }

    private ResponseEntity<String> get(String path, String email) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(email)), String.class);
    }

    private HttpHeaders headers(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenDe(email));
        return headers;
    }

    private String tokenDe(String email) {
        AppUser user = users.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst().orElseThrow();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", user.getTenantId().toString(),
                        "email", email, "password", SENHA), headers),
                String.class);
        try {
            return objectMapper.readTree(resposta.getBody()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
