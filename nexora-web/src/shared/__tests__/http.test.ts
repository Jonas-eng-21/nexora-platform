import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from '../api/http'

describe('http client', () => {
  afterEach(() => vi.restoreAllMocks())

  it('reads the problem+json code even when the failed request asked for a blob', async () => {
    const body = new Blob([JSON.stringify({ code: 'APPLICATION_NOT_FOUND', detail: 'x' })], { type: 'application/problem+json' })
    http.defaults.adapter = () =>
      Promise.reject(Object.assign(new Error('x'), {
        isAxiosError: true,
        response: { status: 404, data: body, headers: {}, statusText: '', config: {} },
        config: { url: '/applications/1/resume' },
      }))
    await expect(http.get('/applications/1/resume', { responseType: 'blob' })).rejects.toMatchObject({
      code: 'APPLICATION_NOT_FOUND',
      status: 404,
    })
  })
})
