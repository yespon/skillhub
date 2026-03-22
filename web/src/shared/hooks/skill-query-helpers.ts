import type { SearchParams } from '@/api/types'
import { WEB_API_PREFIX } from '@/api/client'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

export function normalizeSearchLabels(labels: string[] | string | null | undefined) {
  const source = Array.isArray(labels)
    ? labels
    : typeof labels === 'string'
      ? [labels]
      : []

  return Array.from(
    new Set(
      source
        .map((label) => label.trim().toLowerCase())
        .filter(Boolean)
    )
  ).sort()
}

export function normalizeSearchLabelMode(mode: string | null | undefined): 'any' | 'all' {
  return mode === 'all' ? 'all' : 'any'
}

export function buildSkillSearchUrl(params: SearchParams) {
  const queryParams = new URLSearchParams()
  const normalizedQuery = normalizeSearchQuery(params.q ?? '')
  const normalizedLabels = normalizeSearchLabels(params.labels)

  if (normalizedQuery) {
    queryParams.append('q', normalizedQuery)
  }

  if (params.namespace) {
    const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
    queryParams.append('namespace', cleanNamespace)
  }

  for (const label of normalizedLabels) {
    queryParams.append('label', label)
  }

  if (normalizedLabels.length > 0 && params.labelMode) {
    queryParams.append('labelMode', normalizeSearchLabelMode(params.labelMode))
  }

  if (params.sort) {
    queryParams.append('sort', params.sort)
  }

  if (params.page !== undefined) {
    queryParams.append('page', String(params.page))
  }

  if (params.size !== undefined) {
    queryParams.append('size', String(params.size))
  }

  const queryString = queryParams.toString()
  return queryString ? `${WEB_API_PREFIX}/skills?${queryString}` : `${WEB_API_PREFIX}/skills`
}

export function shouldEnableNamespaceMemberCandidates(slug: string, search: string, enabled = true) {
  return enabled && !!slug && search.trim().length >= 2
}
