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

## Provisório: expediente do prestador

O cálculo de slots (`GET /providers/{id}/slots`) usa um **expediente global configurável**, igual
para todos os prestadores:

```yaml
agendar:
  scheduling:
    expediente:
      inicio: "08:00"
      fim: "18:00"
    slot:
      passo: PT30M
    fuso: UTC
```

Isso existe porque o cadastro de disponibilidade do prestador (horário de trabalho, férias,
bloqueios) ainda não foi feito. **Não é o comportamento final:** quando a disponibilidade por
prestador existir, o `SlotCalculator` passa a receber a janela do prestador em vez desta
configuração.
