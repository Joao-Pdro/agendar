import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';

import { ApiError } from './api-error';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function getAndCaptureError(url = '/test'): Promise<ApiError> {
    const http = TestBed.inject(HttpClient);
    return new Promise<ApiError>((resolve, reject) => {
      http.get(url).subscribe({
        next: () => reject(new Error('esperava erro, recebeu sucesso')),
        error: (error: ApiError) => resolve(error),
      });
    });
  }

  it('converte 409 com ProblemDetail em ApiError', async () => {
    const pending = getAndCaptureError();
    httpMock.expectOne('/test').flush(
      {
        type: 'about:blank',
        title: 'Horário já ocupado',
        status: 409,
        detail: 'O prestador já tem um agendamento nesse horário.',
      },
      {
        status: 409,
        statusText: 'Conflict',
        headers: { 'Content-Type': 'application/problem+json' },
      },
    );

    const error = await pending;
    expect(error.status).toBe(409);
    expect(error.title).toBe('Horário já ocupado');
    expect(error.detail).toBe('O prestador já tem um agendamento nesse horário.');
  });

  it('converte 422 em ApiError com o detail do servidor', async () => {
    const pending = getAndCaptureError();
    httpMock.expectOne('/test').flush(
      { title: 'Dados inválidos', status: 422, detail: 'providerId é obrigatório' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    const error = await pending;
    expect(error).toEqual({
      status: 422,
      title: 'Dados inválidos',
      detail: 'providerId é obrigatório',
    });
  });

  it('converte 500 sem corpo em erro genérico', async () => {
    const pending = getAndCaptureError();
    httpMock.expectOne('/test').flush(null, { status: 500, statusText: 'Server Error' });

    const error = await pending;
    expect(error.status).toBe(500);
    expect(error.title).toBe('Erro no servidor');
    expect(error.detail).toBeUndefined();
  });

  it('converte rede caída em erro de conexão', async () => {
    const pending = getAndCaptureError();
    httpMock
      .expectOne('/test')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    const error = await pending;
    expect(error.status).toBe(0);
    expect(error.title).toBe('Sem conexão com o servidor');
    expect(error.detail).toContain('Verifique a conexão');
  });

  it('usa título padrão quando o corpo não é ProblemDetail', async () => {
    const pending = getAndCaptureError();
    httpMock.expectOne('/test').flush('texto solto', { status: 404, statusText: 'Not Found' });

    const error = await pending;
    expect(error.status).toBe(404);
    expect(error.title).toBe('Não encontrado');
  });
});
