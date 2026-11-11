export const environment = {
  production: false,
  /** URL base da API do backend. Vazio = mesma origem (proxy do dev server). */
  apiUrl: '',
  /**
   * Usa agendamentos de exemplo na grade da agenda, porque ainda não existe
   * `GET /appointments` por prestador e período. Slots continuam reais.
   */
  useMockAgenda: true,
};
