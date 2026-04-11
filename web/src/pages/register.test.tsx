import type { ReactNode } from 'react'
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
    }),
  }
})

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: () => null,
}))

vi.mock('@/features/auth/use-local-auth', () => ({
  useLocalRegister: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: vi.fn(),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
  CardContent: ({ children }: { children: ReactNode }) => children,
  CardDescription: ({ children }: { children: ReactNode }) => children,
  CardHeader: ({ children }: { children: ReactNode }) => children,
  CardTitle: ({ children }: { children: ReactNode }) => children,
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

import { renderToStaticMarkup } from 'react-dom/server'
import { useAuthMethods } from '@/features/auth/use-auth-methods'
import { RegisterPage } from './register'

const mockedUseAuthMethods = vi.mocked(useAuthMethods)

describe('RegisterPage', () => {
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
    expect(typeof RegisterPage).toBe('function')
  })

  it('renders the registration title and form fields', () => {
    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).toContain('register.title')
    expect(html).toContain('register.subtitle')
    expect(html).toContain('register.submit')
  })

  it('defaults to oauth and renders oauth tab before local', () => {
    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).toContain('data-default-value="oauth"')
    expect(html.indexOf('register.tabOAuth')).toBeLessThan(html.indexOf('register.tabLocal'))
  })

  it('hides the local registration tab when local password auth is unavailable', () => {
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

    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).not.toContain('register.tabLocal')
    expect(html).not.toContain('register.submit')
  })
})
