import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MatchBreakdown from '../components/MatchBreakdown.vue'
import MatchScore from '../components/MatchScore.vue'
import type { MatchResult } from '../types'

const base: MatchResult = {
  score: 62, algorithmVersion: 'v1', summary: 'Compatibilidade de 62%: ...', scoreType: 'STRUCTURED', structuredScore: 62, semanticScore: null,
  matchedRequiredSkills: ['Java'], missingRequiredSkills: ['Docker'], matchedDesiredSkills: [], missingDesiredSkills: ['Redis'],
  criteria: [
    { criterion: 'REQUIRED_SKILLS', weight: 50, points: 25, applicable: true, explanation: 'Atende 1 de 2 competências obrigatórias. Faltam: Docker.' },
    { criterion: 'DESIRED_SKILLS', weight: 20, points: 0, applicable: false, explanation: 'A vaga não lista competências desejáveis; o critério não entra no cálculo.' },
  ],
}

describe('MatchScore', () => {
  it('renders the number, a linear progress bar and text for the band (never color alone)', () => {
    const wrapper = mount(MatchScore, { props: { match: base } })
    expect(wrapper.text()).toContain('62/100')
    expect(wrapper.text()).toContain('Afinidade média')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('62')
    expect(wrapper.text()).toContain('Cálculo estruturado')
  })

  it('renders a real zero as 0/100, not as unavailable', () => {
    const wrapper = mount(MatchScore, { props: { match: { ...base, score: 0, structuredScore: 0 } } })
    expect(wrapper.text()).toContain('0/100')
    expect(wrapper.text()).toContain('Baixa afinidade')
    expect(wrapper.text()).not.toContain('indisponível')
  })

  it('renders the unavailable state without any number or bar when there is no match', () => {
    const wrapper = mount(MatchScore, { props: { match: null } })
    expect(wrapper.text()).toContain('Score indisponível')
    expect(wrapper.text()).not.toContain('/100')
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
  })
})

describe('MatchBreakdown', () => {
  it('lists matched and missing skills and each criterion with its explanation', () => {
    const text = mount(MatchBreakdown, { props: { match: base } }).text()
    expect(text).toContain('Obrigatórias que você tem')
    expect(text).toContain('Java')
    expect(text).toContain('Docker')
    expect(text).toContain('Redis')
    expect(text).toContain('25 de 50 pontos')
    expect(text).toContain('Faltam: Docker.')
    expect(text).toContain('Não se aplica a esta vaga')
  })

  it('uses third-person wording and h4 headings when shown to a company', () => {
    const wrapper = mount(MatchBreakdown, { props: { match: base, perspective: 'company', headingTag: 'h4' } })
    const text = wrapper.text()
    expect(text).toContain('Obrigatórias atendidas pelo estudante')
    expect(text).toContain('Obrigatórias ausentes no currículo')
    expect(text).not.toMatch(/você/i)
    expect(wrapper.findAll('h3')).toHaveLength(0)
    expect(wrapper.findAll('h4').length).toBeGreaterThan(0)
  })

  it('keeps the student wording and h3 headings by default', () => {
    const wrapper = mount(MatchBreakdown, { props: { match: base } })
    expect(wrapper.text()).toContain('Obrigatórias que você tem')
    expect(wrapper.findAll('h3').length).toBeGreaterThan(0)
  })

  it('shows structured and semantic scores for HYBRID', () => {
    const text = mount(MatchBreakdown, { props: { match: { ...base, scoreType: 'HYBRID', score: 98, structuredScore: 100, semanticScore: 89 } } }).text()
    expect(text).toContain('Estruturado e semântico')
    expect(text).toContain('100/100')
    expect(text).toContain('Similaridade semântica')
    expect(text).toContain('89/100')
  })

  it('explains FALLBACK honestly and shows no semantic score', () => {
    const text = mount(MatchBreakdown, { props: { match: { ...base, scoreType: 'FALLBACK' } } }).text()
    expect(text).toContain('semântica indisponível')
    expect(text).toContain('vale apenas a nota estruturada')
    expect(text).not.toContain('Similaridade semântica')
  })
})
