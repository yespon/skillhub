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
 * Returns the appropriate icon for a given OAuth provider.
 */
function OAuthIcon({ provider }: { provider: string }) {
  const normalizedProvider = provider.toLowerCase()
  return (
    <img
      src={`/${normalizedProvider}-logo.svg`}
      alt={provider}
      className="w-5 h-5 mr-3"
    />
  )
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
          <OAuthIcon provider={provider.provider} />
          {t('loginButton.loginWith', { name: toProviderLabel(provider, t) })}
        </Button>
      ))}
    </div>
  )
}

