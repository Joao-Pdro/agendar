# Agendar — frontend

Aplicação Angular 17 (standalone components) do sistema de planejamento de serviços.

## Comandos

Rodar a partir de `frontend/`:

```bash
npm install
npm start                  # dev server
npm run build              # build de produção (usa environment.prod.ts)
npm run lint               # ESLint
npm run check:boundaries   # shared/ não pode importar features/
npm run check:http-usage   # só core/http pode importar HttpClient
npm run test:ci            # testes (Karma + Chrome headless)
npm run verify             # tudo acima, na ordem
```

Os testes rodam no browser. Se o Chrome não estiver no PATH:

```bash
CHROME_BIN=/caminho/para/chrome npm run test:ci
```

## Estrutura

```
src/app/
├── core/
│   ├── http/                # cliente HTTP: ApiClient, interceptor de erro, ApiError
│   ├── auth/                # guards e sessão (vem com #5)
│   └── layout/              # ShellComponent
├── shared/                  # reutilizável, sem regra de negócio; NÃO importa features/
└── features/                # uma pasta por domínio, com lazy load
```

## Cliente HTTP

Nenhuma feature injeta `HttpClient`. Injete `ApiClient`:

```ts
private readonly api = inject(ApiClient);

this.api.get<AppointmentView[]>('/scheduling/appointments', {
  params: { providerId, from, to },
});
```

Toda falha chega ao `error` como `ApiError { status, title, detail? }`:

- resposta `application/problem+json` (RFC 7807) vira `{ status, title, detail }`;
- resposta sem corpo vira erro genérico pelo status (`500` → "Erro no servidor");
- falha de rede tem `status: 0` e o título "Sem conexão com o servidor".

A URL base vem de `src/environments/environment.ts` (dev) e
`environment.prod.ts` (produção, trocado via `fileReplacements`).

Erros genéricos de tipagem: use `isApiError()` de `core/http/api-error`.
