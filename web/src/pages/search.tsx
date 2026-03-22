import { startTransition, useEffect, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Loader2, Sparkles, SlidersHorizontal, Tag } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyStars } from '@/shared/hooks/use-user-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { normalizeSearchLabelMode, normalizeSearchLabels } from '@/shared/hooks/skill-query-helpers'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12

/**
 * Skill discovery page with synchronized URL state.
 *
 * Search text, sorting, pagination, and the starred-only filter are mirrored into router search
 * params so the page can be shared, restored, and revisited without losing state.
 */
function filterStarredSkills(skills: SkillSummary[], query: string): SkillSummary[] {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return skills
  }

  return skills.filter((skill) =>
    [skill.preferredDisplayName ?? skill.displayName, skill.summary, skill.namespace, skill.slug]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(normalizedQuery))
  )
}

function sortStarredSkills(skills: SkillSummary[], sort: string): SkillSummary[] {
  const sorted = [...skills]
  if (sort === 'downloads') {
    return sorted.sort((left, right) => right.downloadCount - left.downloadCount)
  }
  if (sort === 'newest' || sort === 'relevance') {
    return sorted.sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
  }
  return sorted
}

export function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated } = useAuth()

  const q = normalizeSearchQuery(searchParams.q || '')
  const selectedLabels = normalizeSearchLabels(searchParams.labels)
  const labelMode = normalizeSearchLabelMode(searchParams.labelMode)
  const sort = searchParams.sort || 'newest'
  const page = searchParams.page ?? 0
  const starredOnly = searchParams.starredOnly ?? false
  const [queryInput, setQueryInput] = useState(q)

  useEffect(() => {
    setQueryInput(q)
  }, [q])

  const { data, isLoading, isFetching } = useSearchSkills({
    q,
    labels: selectedLabels,
    labelMode,
    sort,
    page,
    size: PAGE_SIZE,
    starredOnly,
  })
  const { data: labels } = useVisibleLabels()
  const {
    data: starredSkills,
    isLoading: isLoadingStarred,
    isFetching: isFetchingStarred,
  } = useMyStars(starredOnly && isAuthenticated)

  useEffect(() => {
    // Debounce URL updates while the user is typing so query state stays shareable without
    // triggering a navigation on every keystroke.
    const normalizedQuery = normalizeSearchQuery(queryInput)
    if (normalizedQuery === q) {
      return
    }

    if (!normalizedQuery) {
      startTransition(() => {
        navigate({ to: '/search', search: { q: '', labels: selectedLabels, labelMode, sort, page: 0, starredOnly }, replace: page === 0 })
      })
      return
    }

    const timeoutId = window.setTimeout(() => {
      startTransition(() => {
        navigate({ to: '/search', search: { q: normalizedQuery, labels: selectedLabels, labelMode, sort, page: 0, starredOnly }, replace: true })
      })
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [labelMode, navigate, page, q, queryInput, selectedLabels, sort, starredOnly])

  const handleSearch = (query: string) => {
    const normalizedQuery = normalizeSearchQuery(query)
    setQueryInput(query)
    startTransition(() => {
      navigate({ to: '/search', search: { q: normalizedQuery, labels: selectedLabels, labelMode, sort, page: 0, starredOnly }, replace: true })
    })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { q, labels: selectedLabels, labelMode, sort: newSort, page: 0, starredOnly } })
  }

  const handlePageChange = (newPage: number) => {
    navigate({ to: '/search', search: { q, labels: selectedLabels, labelMode, sort, page: newPage, starredOnly } })
  }

  const handleLabelToggle = (label: string) => {
    const nextLabels = selectedLabels.includes(label)
      ? selectedLabels.filter((item) => item !== label)
      : normalizeSearchLabels([...selectedLabels, label])
    navigate({ to: '/search', search: { q, labels: nextLabels, labelMode, sort, page: 0, starredOnly } })
  }

  const handleLabelModeChange = (mode: 'any' | 'all') => {
    navigate({ to: '/search', search: { q, labels: selectedLabels, labelMode: mode, sort, page: 0, starredOnly } })
  }

  const handleClearLabels = () => {
    navigate({ to: '/search', search: { q, labels: [], labelMode: 'any', sort, page: 0, starredOnly } })
  }

  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({
        to: '/login',
        search: {
          returnTo: `${window.location.pathname}${window.location.search}${window.location.hash}`,
        },
      })
      return
    }

    navigate({ to: '/search', search: { q, labels: selectedLabels, labelMode, sort, page: 0, starredOnly: !starredOnly } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const filteredStarredSkills = starredOnly
    ? sortStarredSkills(filterStarredSkills(starredSkills ?? [], q), sort)
    : []
  const starredPageItems = starredOnly
    ? filteredStarredSkills.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : []
  const totalPages = starredOnly
    ? Math.ceil(filteredStarredSkills.length / PAGE_SIZE)
    : data
      ? Math.ceil(data.total / data.size)
      : 0
  const displayItems = starredOnly ? starredPageItems : (data?.items ?? [])
  const isPageLoading = starredOnly ? isLoadingStarred : isLoading
  const isUpdatingResults = starredOnly ? isFetchingStarred && !isLoadingStarred : isFetching && !isLoading
  const resultCount = starredOnly ? filteredStarredSkills.length : (data?.total ?? 0)
  const facetItems = !starredOnly
    ? (data?.facets?.labels.items ?? labels?.map((label) => ({
        slug: label.slug,
        displayName: label.displayName,
        count: 0,
        selected: selectedLabels.includes(label.slug),
        type: label.type,
      })) ?? [])
    : []
  const selectedFacetCount = selectedLabels.length
  const showFacetPanel = !starredOnly && facetItems.length > 0

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      {/* Search Bar */}
      <div className="max-w-3xl mx-auto">
        <SearchBar
          value={queryInput}
          isSearching={isUpdatingResults}
          onChange={setQueryInput}
          onSearch={handleSearch}
        />
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,280px)_minmax(0,1fr)]">
        <aside className="space-y-4 xl:sticky xl:top-24 xl:self-start">
          <div className="overflow-hidden rounded-[28px] border border-slate-200/80 bg-[linear-gradient(155deg,rgba(255,255,255,0.96),rgba(242,247,255,0.92))] shadow-[0_24px_60px_-36px_rgba(15,23,42,0.45)] backdrop-blur">
            <div className="border-b border-slate-200/70 px-5 py-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">
                    {t('search.facets.eyebrow')}
                  </p>
                  <h2 className="mt-2 flex items-center gap-2 font-serif text-xl text-slate-900">
                    <Tag className="h-4 w-4 text-sky-600" />
                    {t('search.facets.title')}
                  </h2>
                </div>
                <div className="rounded-full border border-slate-200 bg-white/90 px-3 py-1 text-xs font-medium text-slate-600">
                  {t('search.facets.selectedCount', { count: selectedFacetCount })}
                </div>
              </div>
            </div>

            <div className="space-y-5 px-5 py-5">
              <div className="rounded-2xl border border-slate-200/80 bg-white/80 p-3">
                <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                  <SlidersHorizontal className="h-3.5 w-3.5" />
                  {t('search.facets.matchMode')}
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Button
                    variant={labelMode === 'any' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleLabelModeChange('any')}
                  >
                    {t('search.facets.modeAny')}
                  </Button>
                  <Button
                    variant={labelMode === 'all' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleLabelModeChange('all')}
                  >
                    {t('search.facets.modeAll')}
                  </Button>
                </div>
              </div>

              {selectedFacetCount > 0 ? (
                <div className="rounded-2xl border border-sky-100 bg-sky-50/80 p-3">
                  <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-sky-700">
                    <Sparkles className="h-3.5 w-3.5" />
                    {t('search.facets.activeFilters')}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {selectedLabels.map((label) => {
                      const match = facetItems.find((item) => item.slug === label)
                      return (
                        <Button
                          key={label}
                          variant="ghost"
                          size="sm"
                          aria-label={match?.displayName ?? label}
                          className="rounded-full border border-sky-200 bg-white px-3 py-1.5 text-xs font-medium text-sky-700 transition hover:border-sky-400 hover:text-sky-900"
                          onClick={() => handleLabelToggle(label)}
                        >
                          {match?.displayName ?? label}
                        </Button>
                      )
                    })}
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleClearLabels}
                    className="mt-3"
                  >
                    {t('search.facets.clear')}
                  </Button>
                </div>
              ) : null}

              {showFacetPanel ? (
                <div className="space-y-2">
                  {facetItems.map((label) => (
                    <Button
                      key={label.slug}
                      variant={label.selected ? 'default' : 'outline'}
                      size="sm"
                      aria-label={label.displayName}
                      onClick={() => handleLabelToggle(label.slug)}
                      className={`flex w-full items-center justify-between rounded-2xl border px-4 py-3 text-left transition ${label.selected
                        ? 'border-slate-900 bg-slate-900 text-white shadow-[0_18px_40px_-28px_rgba(15,23,42,0.8)]'
                        : 'border-slate-200 bg-white/90 text-slate-700 hover:border-sky-300 hover:bg-sky-50/60 hover:text-slate-900'}`}
                    >
                      <span>
                        <span className="block text-sm font-medium">{label.displayName}</span>
                        <span className={`mt-1 block text-[11px] uppercase tracking-[0.18em] ${label.selected ? 'text-slate-300' : 'text-slate-400'}`}>
                          {label.type}
                        </span>
                      </span>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${label.selected ? 'bg-white/10 text-white' : 'bg-slate-100 text-slate-600'}`}>
                        {label.count}
                      </span>
                    </Button>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-slate-200 bg-white/60 px-4 py-5 text-sm text-slate-500">
                  {t('search.facets.empty')}
                </div>
              )}
            </div>
          </div>
        </aside>

        <section className="space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
            <div className="flex gap-2">
              <Button
                variant={sort === 'relevance' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('relevance')}
              >
                {t('search.sort.relevance')}
              </Button>
              <Button
                variant={sort === 'downloads' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('downloads')}
              >
                {t('search.sort.downloads')}
              </Button>
              <Button
                variant={sort === 'newest' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('newest')}
              >
                {t('search.sort.newest')}
              </Button>
            </div>
          </div>

          {resultCount > 0 && (
            <div className="text-sm text-muted-foreground">
              {t('search.results', { count: resultCount })}
            </div>
          )}
          </div>

          {isUpdatingResults ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>{t('search.loadingMore')}</span>
            </div>
          ) : null}

          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.filters.label')}</span>
            <Button
              variant={starredOnly ? 'default' : 'outline'}
              size="sm"
              onClick={handleStarredToggle}
            >
              {t('search.filterStarred')}
            </Button>
          </div>

          {starredOnly ? (
            <div className="rounded-3xl border border-amber-200/70 bg-amber-50/80 px-5 py-4 text-sm text-amber-900 shadow-[0_18px_40px_-34px_rgba(245,158,11,0.7)]">
              {t('search.facets.starredHint')}
            </div>
          ) : null}

          {isPageLoading ? (
            <SkeletonList count={PAGE_SIZE} />
          ) : displayItems.length > 0 ? (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {displayItems.map((skill, idx) => (
                  <div key={skill.id} className={`h-full animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`}>
                    <SkillCard
                      skill={skill}
                      highlightStarred
                      onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                    />
                  </div>
                ))}
              </div>
              {totalPages > 1 && (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                />
              )}
            </>
          ) : (
            <EmptyState
              title={starredOnly ? t('search.noStarredResults') : t('search.noResults')}
              description={
                starredOnly
                  ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills'))
                  : (q ? t('search.noResultsFor', { q }) : t('search.enterKeyword'))
              }
            />
          )}
        </section>
      </div>
    </div>
  )
}
