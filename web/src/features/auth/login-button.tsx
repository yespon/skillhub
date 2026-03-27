import { useTranslation } from 'react-i18next'
import type { AuthMethod } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { useAuthMethods } from './use-auth-methods'

interface LoginButtonProps {
  returnTo?: string
}

function toProviderLabel(method: AuthMethod, t: (key: string) => string) {
  const normalizedProvider = method.provider.trim().toLowerCase()
  if (normalizedProvider === 'github' || normalizedProvider === 'sourceid') {
    return t(`loginButton.providers.${normalizedProvider}`)
  }

  const rawLabel = method.displayName?.trim() || method.provider.trim()

  return rawLabel
    .replace(/[-_]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

/**
 * Renders OAuth login buttons from the auth-method catalog returned by the backend.
 */
export function LoginButton({ returnTo }: LoginButtonProps) {
  const { t } = useTranslation()
  const { data, isLoading } = useAuthMethods(returnTo)

  const providers = (data ?? []).filter((method) =>
    method.methodType === 'OAUTH_REDIRECT' && method.provider.trim().toLowerCase() === 'sourceid')

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button className="w-full h-12" disabled>
          <div className="w-5 h-5 rounded-full animate-shimmer mr-3" />
          {t('loginButton.loading')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {providers.map((provider) => (
        <Button
          key={provider.id}
          className="w-full h-12 text-base"
          variant="outline"
          onClick={() => {
            window.location.href = provider.actionUrl
          }}
        >
          <svg className="w-5 h-5 mr-3" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z" />
          </svg>
          {t('loginButton.loginWith', { name: toProviderLabel(provider, t) })}
        </Button>
      ))}
    </div>
  )
}
