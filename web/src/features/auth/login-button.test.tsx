import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useAuthMethodsMock = vi.fn()
const buttonLabels: string[] = []

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (key === 'loginButton.providers.github') {
          return 'GitHub'
        }
        if (key === 'loginButton.providers.sourceid') {
          return 'Ruijie SSO'
        }
        if (key === 'loginButton.loginWith') {
          return `Login with ${String(options?.name ?? '')}`
        }
        return key
      },
    }),
  }
})

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: (...args: unknown[]) => useAuthMethodsMock(...args),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, onClick }: { children?: ReactNode, onClick?: () => void }) => {
    const label = Array.isArray(children)
      ? children.filter((child) => typeof child === 'string').join('').trim()
      : typeof children === 'string'
        ? children
        : ''

    buttonLabels.push(label)
    return <button onClick={onClick}>{children}</button>
  },
}))

import { LoginButton } from './login-button'

describe('LoginButton', () => {
  beforeEach(() => {
    useAuthMethodsMock.mockReset()
    buttonLabels.length = 0
  })

  it('renders only the sourceid oauth entry and hides github', () => {
    useAuthMethodsMock.mockReturnValue({
      data: [
        {
          id: 'oauth-github',
          methodType: 'OAUTH_REDIRECT',
          provider: 'github',
          displayName: 'github',
          actionUrl: '/oauth2/authorization/github',
        },
        {
          id: 'oauth-sourceid',
          methodType: 'OAUTH_REDIRECT',
          provider: 'sourceid',
          displayName: 'sourceid',
          actionUrl: '/oauth2/authorization/sourceid',
        },
        {
          id: 'local-password',
          methodType: 'PASSWORD',
          provider: 'local',
          displayName: 'Local Account',
          actionUrl: '/api/v1/auth/local/login',
        },
      ],
      isLoading: false,
    })

    const markup = renderToStaticMarkup(<LoginButton returnTo="/search" />)

    expect(markup).toContain('Login with Ruijie SSO')
    expect(markup).not.toContain('Login with GitHub')
    expect(buttonLabels).toEqual(['Login with Ruijie SSO'])
  })
})