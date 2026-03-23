import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const useSearchMock = vi.fn()
const buttonRecords: Array<{ label: string; variant?: string | null; onClick?: (() => void) | undefined }> = []

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ namespace: 'team-ai' }),
  useSearch: () => useSearchMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (options && typeof options.count === 'number') {
          return `${key}:${options.count}`
        }
        return key
      },
    }),
  }
})

vi.mock('@/features/namespace/namespace-header', () => ({
  NamespaceHeader: () => <div>namespace-header</div>,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => <div>skill-card</div>,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => <div>skeleton</div>,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => <div>empty-state</div>,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    onClick,
    variant,
    'aria-label': ariaLabel,
  }: {
    children?: ReactNode
    onClick?: () => void
    variant?: string
    'aria-label'?: string
  }) => {
    const label = ariaLabel ?? (Array.isArray(children) ? children.join('') : String(children ?? ''))
    buttonRecords.push({ label, variant, onClick })
    return <button data-variant={variant} onClick={onClick}>{children}</button>
  },
}))

const useNamespaceDetailMock = vi.fn()
const useSearchSkillsMock = vi.fn()

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useNamespaceDetail: () => useNamespaceDetailMock(),
  useSearchSkills: () => useSearchSkillsMock(),
}))

import { NamespacePage } from './namespace'

function findButton(label: string) {
  const record = buttonRecords.find((item) => item.label === label)
  if (!record) {
    throw new Error(`Missing button: ${label}`)
  }
  return record
}

describe('NamespacePage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    buttonRecords.length = 0
    useSearchMock.mockReturnValue({ labels: ['official'], labelMode: 'all' })
    useNamespaceDetailMock.mockReturnValue({
      data: { slug: 'team-ai', displayName: 'Team AI' },
      isLoading: false,
    })
    useSearchSkillsMock.mockReturnValue({
      data: {
        items: [{ id: 1, slug: 'demo-skill', displayName: 'Demo Skill' }],
        total: 1,
        facets: {
          labels: {
            mode: 'all',
            items: [
              { slug: 'official', displayName: 'Official', count: 1, selected: true, type: 'RECOMMENDED' },
              { slug: 'code-generation', displayName: 'Code Generation', count: 1, selected: false, type: 'RECOMMENDED' },
            ],
          },
        },
      },
      isLoading: false,
    })
  })

  it('marks the current namespace label mode on initial render', () => {
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(html).toContain('Official')
    expect(findButton('namespace.modeAll').variant).toBe('default')
    expect(findButton('namespace.modeAny').variant).toBe('outline')
  })

  it('adds namespace label filters while keeping the current mode', () => {
    renderToStaticMarkup(<NamespacePage />)

    findButton('Code Generation').onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/space/$namespace',
      params: { namespace: 'team-ai' },
      search: {
        labels: ['code-generation', 'official'],
        labelMode: 'all',
      },
    })
  })

  it('switches the namespace label mode without dropping current labels', () => {
    renderToStaticMarkup(<NamespacePage />)

    findButton('namespace.modeAny').onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/space/$namespace',
      params: { namespace: 'team-ai' },
      search: {
        labels: ['official'],
        labelMode: 'any',
      },
    })
  })
})