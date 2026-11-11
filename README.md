# Agendar

Sistema de planejamento de serviços para empresa de prestação de serviços.

Monolito distribuído: backend Java (Spring Boot) + frontend Angular, no mesmo repositório.

## Estrutura do repositório

```
agendar/
├── backend/             # Maven: pom.xml, platform/, shared/, mvnw
│   ├── platform/        # bootstrap Spring Boot
│   ├── shared/          # tipos compartilhados do backend
│   └── docker-compose.yml  # PostgreSQL
├── frontend/            # aplicação Angular (package.json aqui)
└── README.md
```

Backend e frontend ficam cada um na sua pasta porque têm toolchains diferentes
(Maven e npm) e ciclos de build independentes. A raiz só tem as duas pastas e
este README: nenhum `pom.xml` e nenhum `package.json` lado a lado, e nenhum
build varre o diretório do outro.

## Backend

Java 21 / Spring Boot 3.5, Maven multi-módulo, PostgreSQL com Flyway. Tudo do
backend está em `backend/` (detalhe em [`backend/BACKEND.md`](backend/BACKEND.md)).
Rodar de dentro de `backend/`:

```bash
cd backend
docker compose up -d db
./mvnw -DskipTests install   # instala shared no repositório local
./mvnw spring-boot:run -pl platform
./mvnw test                 # unitários
./mvnw verify               # unitários + integração (Testcontainers, precisa de Docker)
```

## Frontend (Angular)

Todo o código do frontend está em `frontend/`. Rodar a partir de lá:

```bash
cd frontend
npm install
npm start                  # dev server
npm run build              # build de produção
npm run lint               # ESLint (inclui a regra shared/ -> features/)
npm run check:boundaries   # prova que a regra de fronteira está ativa
npm run test:ci            # testes (Karma + Chrome headless)
npm run verify             # lint + boundaries + test + build
```

Estrutura interna:

```
frontend/src/app/
├── core/                    # singleton: layout (shell), auth, http
│   └── layout/              # ShellComponent com nav e router-outlet
├── shared/                  # reutilizável, sem regra de negócio; NÃO importa features/
│   ├── ui/  pipes/  utils/
├── features/                # uma pasta por domínio, carregada por lazy load
│   ├── home/
│   └── scheduling/          # rota /agenda (placeholder)
└── app.routes.ts            # shell + lazy load das features
```

Regra de fronteira: nada em `frontend/src/app/shared/` pode importar de
`frontend/src/app/features/`. É garantida pelo ESLint (`no-restricted-imports`)
em `frontend/eslint.config.js` e comprovada por `npm run check:boundaries`.

Os testes rodam no browser. Se o Chrome não estiver no PATH:

```bash
CHROME_BIN=/caminho/para/chrome npm run test:ci
```
