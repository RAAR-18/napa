import { apiBaseUrl, requestJson } from './api';

describe('requestJson network errors', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it.each([
    new TypeError('Network request failed'),
    new Error('java.net.ConnectException: failed to connect'),
  ])('explains the API address when fetch fails: %s', async (networkError) => {
    globalThis.fetch = jest.fn().mockRejectedValue(networkError);

    await expect(
      requestJson('/api/v1/auth/registro', { method: 'POST' }),
    ).rejects.toThrow(`No se pudo conectar con la API en ${apiBaseUrl}`);
  });
});
