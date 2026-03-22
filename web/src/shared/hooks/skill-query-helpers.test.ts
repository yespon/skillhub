import { describe, expect, it } from 'vitest'
import { buildSkillSearchUrl, normalizeSearchLabelMode, normalizeSearchLabels, shouldEnableNamespaceMemberCandidates } from './skill-query-helpers'

describe('buildSkillSearchUrl', () => {
  it('normalizes the query and strips the namespace prefix', () => {
    expect(buildSkillSearchUrl({
      q: '  hello world  ',
      namespace: '@team-ai',
      labels: ['Official', 'code-generation', 'official'],
      labelMode: 'all',
      sort: 'relevance',
      page: 2,
      size: 12,
    })).toBe('/api/web/skills?q=hello+world&namespace=team-ai&label=code-generation&label=official&labelMode=all&sort=relevance&page=2&size=12')
  })

  it('returns the base skills endpoint when no search params are provided', () => {
    expect(buildSkillSearchUrl({})).toBe('/api/web/skills')
  })
  
  it('normalizes labels and label mode independently', () => {
    expect(normalizeSearchLabels([' Official ', 'code-generation', 'official'])).toEqual(['code-generation', 'official'])
    expect(normalizeSearchLabelMode('all')).toBe('all')
    expect(normalizeSearchLabelMode('unexpected')).toBe('any')
  })
})

describe('shouldEnableNamespaceMemberCandidates', () => {
  it('enables the query only when slug exists and search text has at least two non-space characters', () => {
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'ab')).toBe(true)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', ' a ')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('', 'admin')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'admin', false)).toBe(false)
  })
})
