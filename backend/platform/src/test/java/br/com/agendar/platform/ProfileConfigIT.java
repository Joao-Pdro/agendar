package br.com.agendar.platform;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato dos perfis: o segredo de desenvolvimento só existe no {@code dev}, e o {@code prod}
 * exige {@code JWT_SECRET}.
 *
 * <p>Verifica os arquivos de configuração que a aplicação carrega, com as mesmas regras do Spring
 * (resolução de {@code ${VAR}} e {@code ${VAR:default}}), sem subir um contexto com banco.
 */
class ProfileConfigIT {

    @Test
    void prodSemJwtSecretFalhaAoResolverAoPlaceholder() throws IOException {
        StandardEnvironment env = ambiente("prod", null);

        // O perfil prod declara ${JWT_SECRET} sem valor padrão: sem a variável no ambiente, a
        // aplicação não consegue resolver a propriedade e não sobe — que é o comportamento pedido.
        assertThatThrownBy(() -> env.getProperty("agendar.security.jwt.secret"))
                .isInstanceOf(org.springframework.util.PlaceholderResolutionException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void prodComJwtSecretUsaOValorDoAmbiente() throws IOException {
        StandardEnvironment env = ambiente("prod", "segredo-de-producao-com-mais-de-32-caracteres");

        assertThat(env.getProperty("agendar.security.jwt.secret"))
                .isEqualTo("segredo-de-producao-com-mais-de-32-caracteres");
    }

    @Test
    void devTemSegredoPadraoDeDesenvolvimento() throws IOException {
        StandardEnvironment env = ambiente("dev", null);

        String segredo = env.getProperty("agendar.security.jwt.secret");

        assertThat(segredo).isNotNull();
        assertThat(segredo).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void limiteDeTentativasTemPadraoDeCincoEmQuinzeMinutos() throws IOException {
        StandardEnvironment env = ambiente("dev", null);

        assertThat(env.getProperty("agendar.security.login.max-tentativas", Integer.class)).isEqualTo(5);
        // A janela é ISO-8601 (PT15M), lida pelo Spring Boot como Duration na configuração real.
        assertThat(env.getProperty("agendar.security.login.janela")).isEqualTo("PT15M");
        assertThat(Duration.parse(env.getProperty("agendar.security.login.janela")))
                .isEqualTo(Duration.ofMinutes(15));
    }

    private static org.springframework.core.convert.support.DefaultConversionService conversores() {
        var service = new org.springframework.core.convert.support.DefaultConversionService();
        return service;
    }

    private static StandardEnvironment ambiente(String perfil, String jwtSecret) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(perfil);
        env.setConversionService(org.springframework.boot.context.properties.bind
                .Binder.class == null ? null : conversores());

        if (jwtSecret == null) {
            // Remove qualquer JWT_SECRET que exista na máquina de quem roda o teste.
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        } else {
            env.getPropertySources().addFirst(
                    new org.springframework.core.env.SystemEnvironmentPropertySource(
                            "test-env", java.util.Map.of("JWT_SECRET", jwtSecret)));
        }

        var loader = new YamlPropertySourceLoader();
        for (String arquivo : new String[]{"application.yml", "application-" + perfil + ".yml"}) {
            ClassPathResource resource = new ClassPathResource(arquivo);
            if (resource.exists()) {
                loader.load(arquivo, resource).forEach(env.getPropertySources()::addLast);
            }
        }
        return env;
    }
}
