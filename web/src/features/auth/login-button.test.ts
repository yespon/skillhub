import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginButton } from './login-button'
import { useAuthMethods } from './use-auth-methods'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) => {
      if (key === 'loginButton.loading') {
        return 'Loading'
      }
      if (key === 'loginButton.loginWith') {
        return `Login with ${params?.name ?? ''}`
      }
      return key
    },
  }),
}))

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: vi.fn(),
}))

const mockedUseAuthMethods = vi.mocked(useAuthMethods)

describe('LoginButton', () => {
  beforeEach(() => {
    mockedUseAuthMethods.mockReset()
  })

  it('renders all oauth redirect providers returned by auth methods', () => {
    mockedUseAuthMethods.mockReturnValue({
      data: [
        {
          id: 'oauth-github',
          methodType: 'OAUTH_REDIRECT',
          provider: 'github',
          displayName: 'GitHub',
          actionUrl: '/oauth2/authorization/github',
        },
        {
          id: 'oauth-gitee',
          methodType: 'OAUTH_REDIRECT',
          provider: 'gitee',
          displayName: 'Gitee',
          actionUrl: '/oauth2/authorization/gitee',
        },
        {
          id: 'local-password',
          methodType: 'PASSWORD',
          provider: 'local',
          displayName: 'Password',
          actionUrl: '/api/v1/auth/local/login',
        },
      ],
      isLoading: false,
    } as ReturnType<typeof useAuthMethods>)

    const html = renderToStaticMarkup(LoginButton({ returnTo: '/dashboard' }))

    expect(html).toContain('Login with GitHub')
    expect(html).toContain('Login with Gitee')
    expect(html).not.toContain('Login with Password')
  })

  it('renders a loading button while auth methods are loading', () => {
    mockedUseAuthMethods.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as ReturnType<typeof useAuthMethods>)

    const html = renderToStaticMarkup(LoginButton({}))

    expect(html).toContain('Loading')
  })
})
