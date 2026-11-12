package br.com.agendar.core;

import br.com.agendar.core.internal.provider.ProviderRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cadastro de prestadores (#30).
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProviderCatalogIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    @Autowired private ProviderRepository prestadores;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private UUID tenantA;
    private UUID tenantB;
    private String adminA;
    private String recepcaoA;
    private String prestadorA;
    private String adminB;
    private static final String SENHA = "SenhaForte123";

    @BeforeEach
    void preparar() {
        prestadores.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantA, "A", "pv-a-" + tenantA));
        tenants.saveAndFlush(new Tenant(tenantB, "B", "pv-b-" + tenantB));

        adminA = criarUsuario(tenantA, "adm-a@t.com", Role.ADMIN);
        recepcaoA = criarUsuario(tenantA, "rec-a@t.com", Role.RECEPCAO);
        prestadorA = criarUsuario(tenantA, "pre-a@t.com", Role.PRESTADOR);
        adminB = criarUsuario(tenantB, "adm-b@t.com", Role.ADMIN);
    }

    @Test
    void adminCriaPrestador() throws Exception {
        ResponseEntity<String> resposta = criar(adminA, "João Silva", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode corpo = objectMapper.readTree(resposta.getBody());
        assertThat(corpo.get("name").asText()).isEqualTo("João Silva");
        assertThat(corpo.get("deletedAt").isNull()).isTrue();
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void recepcaoEPrestadorNaoEscrevem() {
        assertThat(criar(recepcaoA, "X", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(criar(prestadorA, "X", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recepcaoLeTodosEPrestadorLeSoOProprio() throws Exception {
        criar(adminA, "Ana", userIdDe(prestadorA));
        criar(adminA, "Bruno", null);

        JsonNode listaRecepcao = objectMapper.readTree(get("/providers", recepcaoA).getBody());
        assertThat(listaRecepcao).hasSize(2);

        JsonNode listaPrestador = objectMapper.readTree(get("/providers", prestadorA).getBody());
        assertThat(listaPrestador).hasSize(1);
        assertThat(listaPrestador.get(0).get("name").asText()).isEqualTo("Ana");
    }

    @Test
    void prestadorNaoVeDetalheDeOutro() throws Exception {
        String idProprio = idDe(criar(adminA, "Ana", userIdDe(prestadorA)));
        String idOutro = idDe(criar(adminA, "Bruno", null));

        ResponseEntity<String> det = get("/providers/" + idProprio, prestadorA);
        assertThat(det.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/providers/" + idOutro, prestadorA).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void isolamentoDeTenant() throws Exception {
        String idA = idDe(criar(adminA, "Ana", null));
        criar(adminB, "Bruno", null);

        JsonNode listaB = objectMapper.readTree(get("/providers", adminB).getBody());
        assertThat(listaB).hasSize(1);

        assertThat(get("/providers/" + idA, adminB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void softDeleteSaiDAListaMasMantemODetalhe() throws Exception {
        String id = idDe(criar(adminA, "Carlos", null));

        ResponseEntity<String> exclusao = rest.exchange("/providers/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers(adminA)), String.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(objectMapper.readTree(get("/providers", adminA).getBody())).isEmpty();

        ResponseEntity<String> detalhe = get("/providers/" + id, adminA);
        assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(detalhe.getBody()).get("deletedAt").isNull()).isFalse();
    }

    @Test
    void patchAltera() throws Exception {
        String id = idDe(criar(adminA, "Carlos", null));

        ResponseEntity<String> alterado = rest.exchange("/providers/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Carlos Souza"), headers(adminA)), String.class);

        assertThat(alterado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(alterado.getBody()).get("name").asText()).isEqualTo("Carlos Souza");
    }

    @Test
    void usuarioDeOutroTenantERecusado() {
        String usuarioDoTenantB = userIdDe(adminB);

        ResponseEntity<String> resposta = criar(adminA, "Carlos", usuarioDoTenantB);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("USUARIO_INVALIDO");
    }

    @Test
    void usuarioInexistenteERecusado() {
        assertThat(criar(adminA, "Carlos", UUID.randomUUID().toString()).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void semTokenResponde401() {
        assertThat(rest.getForEntity("/providers", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- helpers

    private String criarUsuario(UUID tenantId, String email, Role role) {
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, email,
                passwordEncoder.encode(SENHA), email, role));
        return email;
    }

    private String userIdDe(String email) {
        return users.findAll().stream().filter(u -> u.getEmail().equals(email))
                .findFirst().orElseThrow().getId().toString();
    }

    private ResponseEntity<String> criar(String email, String nome, String userId) {
        Map<String, Object> corpo = userId == null
                ? Map.of("name", nome)
                : Map.of("name", nome, "userId", userId);
        return rest.postForEntity("/providers", new HttpEntity<>(corpo, headers(email)), String.class);
    }

    private String idDe(ResponseEntity<String> resposta) throws Exception {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(resposta.getBody()).get("id").asText();
    }

    private ResponseEntity<String> get(String path, String email) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(email)), String.class);
    }

    private HttpHeaders headers(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (email != null) {
            headers.setBearerAuth(tokenDe(email));
        }
        return headers;
    }

    private String tokenDe(String email) {
        AppUser user = users.findAll().stream().filter(u -> u.getEmail().equals(email))
                .findFirst().orElseThrow();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", user.getTenantId().toString(),
                        "email", email, "password", SENHA), h), String.class);
        try {
            return objectMapper.readTree(resposta.getBody()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
