import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({ returnTo: '' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  Eye: () => null,
  EyeOff: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => ({ enabled: false }),
}))

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: () => null,
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => null,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: vi.fn(),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children, defaultValue }: { children: ReactNode; defaultValue?: string }) => (
    <div data-default-value={defaultValue}>{children}</div>
  ),
  TabsContent: ({ children }: { children: ReactNode }) => children,
  TabsList: ({ children }: { children: ReactNode }) => children,
  TabsTrigger: ({ children }: { children: ReactNode }) => children,
}))

import { LoginPage } from './login'
import { useAuthMethods } from '@/features/auth/use-auth-methods'

const mockedUseAuthMethods = vi.mocked(useAuthMethods)

describe('LoginPage', () => {
  beforeEach(() => {
    mockedUseAuthMethods.mockReset()
    mockedUseAuthMethods.mockReturnValue({
      data: [
        {
          id: 'local-password',
          methodType: 'PASSWORD',
          provider: 'local',
          displayName: 'Local Account',
          actionUrl: '/api/v1/auth/local/login',
        },
        {
          id: 'oauth-sourceid',
          methodType: 'OAUTH_REDIRECT',
          provider: 'sourceid',
          displayName: '锐捷SSO',
          actionUrl: '/oauth2/authorization/sourceid',
        },
      ],
    } as ReturnType<typeof useAuthMethods>)
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.submit')
  })

  it('defaults to oauth and renders oauth tab before password', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('data-default-value="oauth"')
    expect(html.indexOf('login.tabOAuth')).toBeLessThan(html.indexOf('login.tabPassword'))
  })

  it('hides password registration entry when local password method is unavailable', () => {
    mockedUseAuthMethods.mockReturnValue({
      data: [
        {
          id: 'oauth-sourceid',
          methodType: 'OAUTH_REDIRECT',
          provider: 'sourceid',
          displayName: '锐捷SSO',
          actionUrl: '/oauth2/authorization/sourceid',
        },
      ],
    } as ReturnType<typeof useAuthMethods>)

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).not.toContain('login.tabPassword')
    expect(html).not.toContain('login.register')
  })
})
