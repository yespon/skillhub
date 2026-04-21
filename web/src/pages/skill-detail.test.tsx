import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const hasRoleMock = vi.fn((role: string) => role === 'USER')
const useSkillDetailMock = vi.fn()
const useSkillLabelsMock = vi.fn()
const useSkillTranslationsMock = vi.fn()
const buttonRecords: Array<{ label: string; onClick?: (() => void) | undefined }> = []
const useSkillVersionsMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ namespace: 'global', slug: 'demo-skill' }),
  useRouterState: () => ({ pathname: '/space/global/demo-skill', searchStr: '', hash: '' }),
  useSearch: () => ({ returnTo: '/dashboard/skills' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'zh' },
    }),
  }
})

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({ data: undefined, isLoading: false }),
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    user: { userId: 'owner-1', platformRoles: ['USER'] },
    hasRole: hasRoleMock,
  }),
}))

vi.mock('@/features/report/use-skill-reports', () => ({
  useSubmitSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/shared/ui/button', async () => {
  const actual = await vi.importActual<typeof import('@/shared/ui/button')>('@/shared/ui/button')
  return {
    ...actual,
    Button: ({
      children,
      onClick,
      'aria-label': ariaLabel,
    }: {
      children?: React.ReactNode
      onClick?: () => void
      'aria-label'?: string
    }) => {
      const label = ariaLabel ?? (Array.isArray(children) ? children.join('') : String(children ?? ''))
      buttonRecords.push({ label, onClick })
      return <button onClick={onClick}>{children}</button>
    },
  }
})

vi.mock('@/api/client', () => ({
  adminApi: {
    hideSkill: vi.fn(),
    unhideSkill: vi.fn(),
    yankVersion: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    serverMessageKey?: string
  },
  buildApiUrl: (value: string) => value,
  WEB_API_PREFIX: '/api/web',
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/skill-download-cache', () => ({
  incrementSkillDownloadCount: vi.fn(),
}))

vi.mock('@/shared/lib/number-format', () => ({
  formatCompactCount: (value: number) => String(value),
}))

vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: () => <div>markdown</div>,
}))

vi.mock('@/features/skill/file-tree', () => ({
  FileTree: () => <div>files</div>,
}))

vi.mock('@/features/skill/install-command', () => ({
  InstallCommand: () => <div>install</div>,
}))

vi.mock('@/features/skill/skill-label-panel', () => ({
  SkillLabelPanel: ({ canManage }: { canManage: boolean }) => canManage
    ? <div>skillDetail.labelsSectionTitle skillDetail.removeLabel skillDetail.addLabel</div>
    : null,
}))

vi.mock('@/features/social/rating-input', () => ({
  RatingInput: () => <div>__RATING_WIDGET__</div>,
}))

vi.mock('@/features/social/star-button', () => ({
  StarButton: () => <div>__STAR_WIDGET__</div>,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillDetail: () => useSkillDetailMock(),
  useSkillLabels: () => useSkillLabelsMock(),
  useSkillTranslations: () => useSkillTranslationsMock(),
  useVisibleLabels: () => ({
    data: [{ slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' }],
    isLoading: false,
  }),
  useAdminLabelDefinitions: () => ({ data: [], isLoading: false }),
  useAttachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useUpsertSkillTranslation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteSkillTranslation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSkillVersions: (...args: unknown[]) => useSkillVersionsMock(...args),
  useSkillVersionDetail: () => ({ data: undefined }),
  useSkillFiles: () => ({ data: [] }),
  useSkillReadme: () => ({ data: '# Demo', error: null }),
  useArchiveSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteSkillVersion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRereleaseSkillVersion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSubmitPromotion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnarchiveSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useWithdrawSkillReview: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSubmitForReview: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useConfirmPublish: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSubmitSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { SkillDetailPage } from './skill-detail'

function createSkill(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    slug: 'demo-skill',
    displayName: 'Demo Skill',
    ownerId: 'owner-1',
    ownerDisplayName: 'Owner One',
    summary: 'summary',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    downloadCount: 12,
    starCount: 2,
    ratingAvg: 4.5,
    ratingCount: 2,
    hidden: false,
    namespace: 'global',
    canManageLifecycle: true,
    canSubmitPromotion: false,
    canInteract: true,
    canReport: true,
    headlineVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
    publishedVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
    ownerPreviewVersion: undefined,
    resolutionMode: 'PUBLISHED',
    ...overrides,
  }
}

describe('SkillDetailPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    buttonRecords.length = 0
    hasRoleMock.mockImplementation((role: string) => role === 'USER')
    useSkillDetailMock.mockReturnValue({
      data: createSkill(),
      isLoading: false,
      isFetching: false,
      error: null,
    })
    useSkillVersionsMock.mockReturnValue({
      data: [
        {
          id: 10,
          version: '1.0.0',
          status: 'PUBLISHED',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: '2026-03-20T00:00:00Z',
          downloadAvailable: true,
        },
      ],
    })
    useSkillLabelsMock.mockReturnValue({
      data: undefined,
    })
    useSkillTranslationsMock.mockReturnValue({
      data: [],
    })
  })

  it('shows hard delete action for the skill owner', () => {
    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.deleteSkill')
  })

  it('hides hard delete action when the viewer is not the owner or super admin', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({ ownerId: 'someone-else' }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).not.toContain('skillDetail.deleteSkill')
  })

  it('shows the label management panel for a user who can manage the skill lifecycle', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        labels: [{ slug: 'official', type: 'RECOMMENDED', displayName: 'Official' }],
      }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.labelsSectionTitle')
    expect(html).toContain('skillDetail.removeLabel')
    expect(html).toContain('skillDetail.addLabel')
  })

  it('shows the zh-CN translation management card for lifecycle managers', () => {
    useSkillTranslationsMock.mockReturnValue({
      data: [{ locale: 'zh-cn', displayName: '演示技能', sourceType: 'USER', updatedAt: '2026-03-22T00:00:00Z' }],
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.translationSectionTitle')
    expect(html).toContain('演示技能')
    expect(html).toContain('skillDetail.translationEditAction')
  })
  it('navigates to search with the clicked label filter', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        labels: [{ slug: 'official', type: 'RECOMMENDED', displayName: 'Official' }],
      }),
      isLoading: false,
      error: null,
    })

    renderToStaticMarkup(<SkillDetailPage />)

    const labelButton = buttonRecords.find((button) => button.label === 'Official')
    labelButton?.onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/search',
      search: {
        q: '',
        labels: ['official'],
        labelMode: 'any',
        sort: 'relevance',
        page: 0,
        starredOnly: false,
      },
    })
  })

  it('hides the label management panel when the viewer lacks label permissions', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        ownerId: 'someone-else',
        canManageLifecycle: false,
      }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).not.toContain('skillDetail.labelsSectionTitle')
  })

  it('does not render dependent social controls while the detail query is still refetching', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill(),
      isLoading: false,
      isFetching: true,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(useSkillVersionsMock).toHaveBeenCalledWith('global', 'demo-skill', false)
    expect(html).not.toContain('__STAR_WIDGET__')
    expect(html).not.toContain('__RATING_WIDGET__')
  })
})
