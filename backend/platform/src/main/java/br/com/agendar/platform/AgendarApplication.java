package br.com.agendar.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Bootstrap da aplicação.
 *
 * <p>O scan cobre os pacotes dos módulos de domínio explicitamente: cada módulo vive em
 * {@code br.com.agendar.<modulo>} e é uma biblioteca, não uma aplicação Spring Boot própria.
 */
@SpringBootApplication(scanBasePackages = {
        "br.com.agendar.platform",
        "br.com.agendar.identity"
})
@EnableJpaRepositories(basePackages = "br.com.agendar.identity")
@EntityScan(basePackages = "br.com.agendar.identity")
public class AgendarApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendarApplication.class, args);
    }
}
