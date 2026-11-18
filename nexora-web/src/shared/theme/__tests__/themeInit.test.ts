import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

// Regressão da CSP forte: o index.html não pode ter script inline (script-src 'self' sem 'unsafe-inline'),
// e o tema salvo continua sendo aplicado antes da pintura por um script externo.
const root = resolve(__dirname, '../../../..')
const html = readFileSync(resolve(root, 'index.html'), 'utf8')

describe('index.html e CSP', () => {
  it('não tem nenhum <script> inline nem handler on*=', () => {
    const inline = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)].filter(
      ([, attrs, body]) => !/\bsrc=/.test(attrs) || body.trim() !== '',
    )
    expect(inline).toEqual([])
    expect(html).not.toMatch(/\son[a-z]+\s*=/i)
  })

  it('carrega o script do tema de forma síncrona e externa, no <head>', () => {
    const head = html.slice(0, html.indexOf('</head>'))
    expect(head).toMatch(/<script src="\/theme-init\.js"><\/script>/)
  })
})

describe('theme-init.js', () => {
  const code = readFileSync(resolve(root, 'public/theme-init.js'), 'utf8')
  const run = () => new Function(code)()
  afterEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  it('aplica o tema escuro salvo', () => {
    localStorage.setItem('nexora-theme', 'dark')
    run()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('mantém o tema claro salvo mesmo que o sistema prefira escuro', () => {
    localStorage.setItem('nexora-theme', 'light')
    window.matchMedia = (() => ({ matches: true })) as unknown as typeof window.matchMedia
    run()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('sem preferência salva, segue o sistema', () => {
    window.matchMedia = (() => ({ matches: true })) as unknown as typeof window.matchMedia
    run()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('não quebra quando o localStorage está indisponível', () => {
    const original = Object.getOwnPropertyDescriptor(window, 'localStorage')!
    Object.defineProperty(window, 'localStorage', { get() { throw new Error('blocked') }, configurable: true })
    try {
      expect(run).not.toThrow()
    } finally {
      Object.defineProperty(window, 'localStorage', original)
    }
  })
})
