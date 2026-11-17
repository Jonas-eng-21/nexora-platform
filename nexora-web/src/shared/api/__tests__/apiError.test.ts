import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { toApiError } from '../apiError'

function failure(status: number, data: unknown): AxiosError {
  const response = { status, data, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } } as AxiosResponse
  return new AxiosError('boom', 'ERR_BAD_REQUEST', response.config, null, response)
}

describe('toApiError', () => {
  it('keeps the stable code and maps field errors from application/problem+json', () => {
    const error = toApiError(
      failure(400, { code: 'VALIDATION_ERROR', detail: 'x', errors: [{ field: 'university.name', message: 'não pode estar em branco' }] }),
    )
    expect(error.code).toBe('VALIDATION_ERROR')
    expect(error.fieldErrors['university.name']).toBe('não pode estar em branco')
  })

  it('translates known codes to a useful message instead of the raw detail', () => {
    expect(toApiError(failure(401, { code: 'INVALID_CREDENTIALS', detail: 'x' })).message).toMatch(/incorretos/)
    expect(toApiError(failure(403, { code: 'ACCOUNT_PENDING_APPROVAL', detail: 'x' })).message).toMatch(/aprovação/)
  })

  it('uses the API detail for codes without a local message', () => {
    expect(toApiError(failure(404, { code: 'SOMETHING_NEW', detail: 'Recurso não encontrado.' })).message).toBe('Recurso não encontrado.')
  })

  it('treats a 5xx without a backend code (proxy without upstream) as unavailable', () => {
    const error = toApiError(failure(500, ''))
    expect(error.code).toBe('SERVICE_UNAVAILABLE')
    expect(error.isUnavailable).toBe(true)
  })

  it('treats a request without response as a network error', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'))
    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.isUnavailable).toBe(true)
  })

  it('does not echo response bodies of unknown shape', () => {
    expect(toApiError(failure(400, '<html>token=abc</html>')).message).not.toMatch(/token/)
  })
})
