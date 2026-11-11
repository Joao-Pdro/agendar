/**
 * Erro tipado devolvido por todas as chamadas HTTP da aplicação.
 *
 * Segue o formato do ProblemDetail (RFC 7807) que o backend devolve:
 * `{ type?, title, status, detail?, instance? }`.
 */
export interface ApiError {
  /** Código HTTP da resposta. `0` quando a requisição não chegou ao servidor. */
  readonly status: number;
  /** Resumo curto do problema. Em erro de rede, uma mensagem fixa em português. */
  readonly title: string;
  /** Explicação do que deu errado, quando o servidor manda. */
  readonly detail?: string;
}

/** Valores de `status` para falhas que não vêm de uma resposta HTTP. */
export const NETWORK_ERROR_STATUS = 0;
export const CLIENT_ERROR_STATUS = -1;

/** Problema no formato RFC 7807, como o backend envia. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
}

const DEFAULT_TITLES: Record<number, string> = {
  0: 'Sem conexão com o servidor',
  400: 'Requisição inválida',
  401: 'Não autenticado',
  403: 'Acesso negado',
  404: 'Não encontrado',
  409: 'Conflito',
  422: 'Dados inválidos',
  500: 'Erro no servidor',
};

function defaultTitle(status: number): string {
  return DEFAULT_TITLES[status] ?? 'Erro inesperado';
}

function isProblemDetail(body: unknown): body is ProblemDetail {
  return typeof body === 'object' && body !== null;
}

/**
 * Converte o corpo de uma resposta de erro em `ApiError`.
 * Corpo vazio ou que não é ProblemDetail vira um erro genérico pelo status.
 */
export function toApiError(status: number, body: unknown): ApiError {
  if (!isProblemDetail(body)) {
    return { status, title: defaultTitle(status) };
  }

  const title =
    typeof body.title === 'string' && body.title.trim() !== ''
      ? body.title
      : defaultTitle(status);
  const detail =
    typeof body.detail === 'string' && body.detail.trim() !== ''
      ? body.detail
      : undefined;

  return detail === undefined ? { status, title } : { status, title, detail };
}

/** Erro para quando a requisição nem chegou ao servidor (rede caiu, CORS, timeout). */
export function networkApiError(): ApiError {
  return {
    status: NETWORK_ERROR_STATUS,
    title: defaultTitle(NETWORK_ERROR_STATUS),
    detail: 'Não foi possível falar com o servidor. Verifique a conexão.',
  };
}

/** Tipo guard para quem recebe `unknown` e precisa saber se é `ApiError`. */
export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiError).status === 'number' &&
    typeof (value as ApiError).title === 'string'
  );
}
