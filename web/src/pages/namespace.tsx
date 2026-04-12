import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SlidersHorizontal, Tag } from 'lucide-react'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Button } from '@/shared/ui/button'
import { useNamespaceDetail, useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchLabelMode, normalizeSearchLabels } from '@/shared/hooks/skill-query-helpers'

/**
 * Public namespace page showing namespace metadata and the skills currently discoverable inside it.
 */
export function NamespacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { namespace } = useParams({ from: '/space/$namespace' })
  const searchParams = useSearch({ from: '/space/$namespace' })
  const selectedLabels = normalizeSearchLabels(searchParams.labels)
  const labelMode = normalizeSearchLabelMode(searchParams.labelMode)

  const { data: namespaceData, isLoading: isLoadingNamespace } = useNamespaceDetail(namespace)
  const { data: skillsData, isLoading: isLoadingSkills } = useSearchSkills({
    namespace,
    labels: selectedLabels,
    labelMode,
    size: 20,
  }, { enabledWhenEmpty: true })

  const facetItems = skillsData?.facets?.labels.items ?? []

  const handleLabelToggle = (label: string) => {
    const nextLabels = selectedLabels.includes(label)
      ? selectedLabels.filter((item) => item !== label)
      : normalizeSearchLabels([...selectedLabels, label])
    navigate({ to: '/space/$namespace', params: { namespace }, search: { labels: nextLabels, labelMode } })
  }

  const handleLabelModeChange = (mode: 'any' | 'all') => {
    navigate({ to: '/space/$namespace', params: { namespace }, search: { labels: selectedLabels, labelMode: mode } })
  }

  const handleClearLabels = () => {
    navigate({ to: '/space/$namespace', params: { namespace }, search: { labels: [], labelMode: 'any' } })
  }

  const handleSkillClick = (slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespaceData) {
    return <EmptyState title={t('namespace.notFound')} />
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceHeader namespace={namespaceData} />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,260px)_minmax(0,1fr)]">
        <aside className="space-y-4 xl:sticky xl:top-24 xl:self-start">
          <div className="overflow-hidden rounded-[28px] border border-slate-200/80 bg-[linear-gradient(160deg,rgba(255,255,255,0.96),rgba(245,250,255,0.92))] shadow-[0_24px_60px_-36px_rgba(15,23,42,0.45)] backdrop-blur">
            <div className="border-b border-slate-200/70 px-5 py-4">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">
                {t('namespace.filtersEyebrow')}
              </p>
              <h2 className="mt-2 flex items-center gap-2 font-serif text-xl text-slate-900">
                <Tag className="h-4 w-4 text-sky-600" />
                {t('namespace.filtersTitle')}
              </h2>
            </div>
            <div className="space-y-4 px-5 py-5">
              <div className="rounded-2xl border border-slate-200/80 bg-white/80 p-3">
                <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                  <SlidersHorizontal className="h-3.5 w-3.5" />
                  {t('namespace.matchMode')}
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Button
                    variant={labelMode === 'any' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleLabelModeChange('any')}
                  >
                    {t('namespace.modeAny')}
                  </Button>
                  <Button
                    variant={labelMode === 'all' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleLabelModeChange('all')}
                  >
                    {t('namespace.modeAll')}
                  </Button>
                </div>
              </div>

              {selectedLabels.length > 0 ? (
                <div className="rounded-2xl border border-sky-100 bg-sky-50/80 p-3">
                  <div className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-sky-700">
                    {t('namespace.activeFilters')}
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
                          className="rounded-full border border-sky-200 bg-white px-3 py-1.5 text-xs font-medium text-sky-700"
                          onClick={() => handleLabelToggle(label)}
                        >
                          {match?.displayName ?? label}
                        </Button>
                      )
                    })}
                  </div>
                  <Button variant="ghost" size="sm" className="mt-3" onClick={handleClearLabels}>
                    {t('namespace.clearFilters')}
                  </Button>
                </div>
              ) : null}

              {facetItems.length > 0 ? (
                <div className="space-y-2">
                  {facetItems.map((label) => (
                    <Button
                      key={label.slug}
                      variant={label.selected ? 'default' : 'outline'}
                      size="sm"
                      aria-label={label.displayName}
                      onClick={() => handleLabelToggle(label.slug)}
                      className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left"
                    >
                      <span className="text-sm font-medium">{label.displayName}</span>
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                        {label.count}
                      </span>
                    </Button>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-slate-200 bg-white/60 px-4 py-5 text-sm text-slate-500">
                  {t('namespace.noFacetFilters')}
                </div>
              )}
            </div>
          </div>
        </aside>

        <section className="space-y-6">
          <div className="flex items-center justify-between gap-4">
            <h2 className="text-2xl font-bold font-heading">{t('namespace.skillList')}</h2>
            <div className="text-sm text-muted-foreground">
              {t('namespace.resultsCount', { count: skillsData?.total ?? 0 })}
            </div>
          </div>
          {isLoadingSkills ? (
            <SkeletonList count={6} />
          ) : skillsData && skillsData.items.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {skillsData.items.map((skill, idx) => (
                <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <SkillCard
                    skill={skill}
                    onClick={() => handleSkillClick(skill.slug)}
                  />
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              title={t('namespace.emptyTitle')}
              description={t('namespace.emptyDescription')}
            />
          )}
        </section>
      </div>
    </div>
  )
}
