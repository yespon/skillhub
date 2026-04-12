import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import {
  getAdminLabelDefinitionsQueryKey,
  getSkillDetailQueryKey,
  getSkillLabelsQueryKey,
  getVisibleLabelsQueryKey,
} from './query-keys'

const useQueryMock = vi.fn()

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: unknown) => useQueryMock(options),
  useMutation: vi.fn(),
  useQueryClient: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  fetchJson: vi.fn(),
  fetchText: vi.fn(),
  getCsrfHeaders: vi.fn(() => ({})),
  labelApi: {},
  meApi: {},
  namespaceApi: {},
  promotionApi: {},
  skillLifecycleApi: {},
  skillTranslationApi: {},
  WEB_API_PREFIX: '/api/web',
}))

vi.mock('@/i18n/config', () => ({
  default: {
    resolvedLanguage: 'en',
    language: 'en',
  },
}))

vi.mock('./skill-query-helpers', () => ({
  buildSkillSearchUrl: vi.fn(() => '/api/web/skills/search'),
  shouldEnableNamespaceMemberCandidates: vi.fn(() => true),
}))

import { useSearchSkills } from './use-skill-queries'

describe('useSearchSkills', () => {
  beforeEach(() => {
    useQueryMock.mockReset()
    useQueryMock.mockReturnValue({ data: undefined, isLoading: false })
  })

  it('keeps empty global search disabled by default', () => {
    useSearchSkills({ sort: 'newest', size: 6 })

    expect(useQueryMock).toHaveBeenCalledWith(expect.objectContaining({ enabled: false }))
  })

  it('allows empty queries when the caller opts in', () => {
    useSearchSkills({ namespace: 'team-ai', size: 20 }, { enabledWhenEmpty: true })

    expect(useQueryMock).toHaveBeenCalledWith(expect.objectContaining({ enabled: true }))
  })

  it('still enables filtered searches without the opt-in flag', () => {
    useSearchSkills({ labels: ['official'], labelMode: 'all' })

    expect(useQueryMock).toHaveBeenCalledWith(expect.objectContaining({ enabled: true }))
  })
})

describe('localized label query keys', () => {
  const originalLanguage = i18n.language
  const originalResolvedLanguage = i18n.resolvedLanguage

  afterEach(() => {
    i18n.language = originalLanguage
    i18n.resolvedLanguage = originalResolvedLanguage
  })

  it('includes the current language so localized label data refetches after language switches', () => {
    i18n.language = 'en'
    i18n.resolvedLanguage = 'en'

    expect(getVisibleLabelsQueryKey()).toEqual(['labels', 'visible', 'en'])
    expect(getSkillLabelsQueryKey('team', 'demo')).toEqual(['labels', 'skill', 'team', 'demo', 'en'])
    expect(getSkillDetailQueryKey('team', 'demo')).toEqual(['skills', 'team', 'demo', 'en'])
    expect(getAdminLabelDefinitionsQueryKey()).toEqual(['labels', 'admin', 'en'])

    i18n.language = 'zh-CN'
    i18n.resolvedLanguage = 'zh-CN'

    expect(getVisibleLabelsQueryKey()).toEqual(['labels', 'visible', 'zh-CN'])
    expect(getSkillLabelsQueryKey('team', 'demo')).toEqual(['labels', 'skill', 'team', 'demo', 'zh-CN'])
    expect(getSkillDetailQueryKey('team', 'demo')).toEqual(['skills', 'team', 'demo', 'zh-CN'])
    expect(getAdminLabelDefinitionsQueryKey()).toEqual(['labels', 'admin', 'zh-CN'])
  })
})
