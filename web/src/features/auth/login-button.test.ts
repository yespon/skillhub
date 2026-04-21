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

  it('renders only sourceid oauth redirect providers', () => {
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
          id: 'oauth-sourceid',
          methodType: 'OAUTH_REDIRECT',
          provider: 'sourceid',
          displayName: 'SourceID',
          actionUrl: '/oauth2/authorization/sourceid',
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

    expect(html).not.toContain('Login with GitHub')
    expect(html).not.toContain('Login with Password')
    expect(html).toContain('sourceid')
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
