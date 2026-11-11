# Backend

Java 21 / Spring Boot 3.5, Maven multi-módulo, PostgreSQL com Flyway.

## Módulos

| Módulo | Papel |
|---|---|
| `platform` | bootstrap: `AgendarApplication`, configuração, actuator |
| `shared` | tipos-base compartilhados (sem dependência de framework de domínio) |

Novos módulos de domínio (`identity`, `scheduling`, ...) entram no `<modules>`
do `backend/pom.xml` e publicam só `api/`; o resto fica em `internal/`.

## Como rodar

Todos os comandos abaixo rodam de dentro de `backend/`.

```bash
docker compose up -d db
./mvnw -DskipTests install          # instala shared no repositório local
./mvnw spring-boot:run -pl platform
curl localhost:8080/actuator/health
```

`-pl platform` sozinho exige o `shared` já instalado (`./mvnw install`).
`./mvnw -am spring-boot:run -pl platform` não funciona: o goal `run` também
roda no módulo pai e falha por não ter main class.

Variáveis de ambiente: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`.

## Testes

```bash
./mvnw test     # testes unitários (*Test)
./mvnw verify   # unitários + integração (*IT, com Testcontainers/PostgreSQL)
```

Testcontainers requer Docker rodando.

## Migrations

Flyway ligado em `classpath:db/migration`. Cada módulo de domínio carrega as
suas migrations a partir de `db/migration/<modulo>/`, com schema e tabela de
histórico próprios (`flyway_schema_history_<modulo>`), para que os `V1__` de
módulos diferentes não colidam.
