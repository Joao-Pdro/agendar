# Agendar

Sistema de planejamento de servicos para empresa de prestacao de servicos.

Monolito distribuido: backend Java (Spring Boot) + frontend Angular.

Backend: Java 21 / Spring Boot 3.5, Maven multi-modulo, PostgreSQL, Flyway.

## Modulos do backend

| Módulo | Papel |
|---|---|
| `platform` | bootstrap: `AgendarApplication`, configuração, actuator |
| `shared` | tipos-base compartilhados (sem dependência de framework de domínio) |

Novos módulos de domínio (`identity`, `scheduling`, ...) entram no `<modules>` do `pom.xml` da raiz e publicam só `api/`; o resto fica em `internal/`.

## Como rodar o backend

```bash
docker compose up -d db
./mvnw -DskipTests install          # instala shared no repo local
./mvnw spring-boot:run -pl platform
curl localhost:8080/actuator/health
```

`-pl platform` sozinho exige o `shared` já instalado (`./mvnw install`). `./mvnw -am spring-boot:run -pl platform`
não funciona: o goal `run` também roda no módulo pai e falha por não ter main class.

## Testes do backend

```bash
./mvnw test     # testes unitários (*Test)
./mvnw verify   # unitários + integração (*IT, com Testcontainers/PostgreSQL)
```

Testcontainers requer Docker rodando. As migrations Flyway de cada módulo ficam em
`<modulo>/src/main/resources/db/migration` e são carregadas pelo `platform`.
