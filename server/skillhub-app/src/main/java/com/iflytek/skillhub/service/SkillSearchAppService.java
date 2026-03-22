package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service that assembles discovery responses from search matches.
 *
 * <p>{@link com.iflytek.skillhub.search.SearchQueryService} remains the match
 * engine, while this service enriches matched ids into API-facing summaries.
 * Authoritative detail, version, and file reads remain in
 * {@link com.iflytek.skillhub.domain.skill.service.SkillQueryService}.
 */
@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            NamespaceService namespaceService,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            LabelDefinitionService labelDefinitionService,
            LabelLocalizationService labelLocalizationService) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public record SearchResponse(
            List<SkillSummaryResponse> items,
            long total,
            int page,
            int size,
            SearchFacetResponse facets,
            AppliedFiltersResponse appliedFilters
    ) {
        public SearchResponse(List<SkillSummaryResponse> items, long total, int page, int size) {
            this(items, total, page, size, SearchFacetResponse.empty(), AppliedFiltersResponse.empty());
        }
    }

    public record SearchFacetResponse(LabelFacetGroupResponse labels) {
        public static SearchFacetResponse empty() {
            return new SearchFacetResponse(new LabelFacetGroupResponse("any", List.of()));
        }
    }

    public record LabelFacetGroupResponse(String mode, List<LabelFacetItemResponse> items) {
    }

    public record LabelFacetItemResponse(String slug, String displayName, long count, boolean selected, String type) {
    }

    public record AppliedFiltersResponse(List<String> labels, String labelMode, String namespace, String sort) {
        public static AppliedFiltersResponse empty() {
            return new AppliedFiltersResponse(List.of(), "any", null, null);
        }
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        return search(keyword, namespaceSlug, sortBy, page, size, List.of(), "any", true, userId, userNsRoles);
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        return search(keyword, namespaceSlug, sortBy, page, size, labelSlugs, "any", true, userId, userNsRoles);
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String labelMode,
            boolean includeFacets,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {

        Long namespaceId = resolveNamespaceId(namespaceSlug, userId, userNsRoles);

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        return searchVisibleSkills(
                keyword,
                namespaceId,
                namespaceSlug,
                sortBy != null ? sortBy : "newest",
                page,
                size,
                labelSlugs,
                normalizeLabelMode(labelMode),
                includeFacets,
                scope);
    }

    private Long resolveNamespaceId(String namespaceSlug, String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (namespaceSlug == null || namespaceSlug.isBlank()) {
            return null;
        }
        return namespaceService.getNamespaceBySlugForRead(namespaceSlug, userId, userNsRoles != null ? userNsRoles : Map.of()).getId();
    }

    private SearchVisibilityScope buildVisibilityScope(String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (userId == null || userNsRoles == null) {
            return SearchVisibilityScope.anonymous();
        }

        Set<Long> memberNamespaceIds = userNsRoles.keySet();
        Set<Long> adminNamespaceIds = userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        adminNamespaceIds.addAll(userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.OWNER)
                .map(Map.Entry::getKey)
                .toList());

        return new SearchVisibilityScope(userId, memberNamespaceIds, adminNamespaceIds);
    }

    private SearchResponse searchVisibleSkills(
            String keyword,
            Long namespaceId,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String labelMode,
            boolean includeFacets,
            SearchVisibilityScope scope) {
        List<String> normalizedLabelSlugs = normalizeLabelSlugs(labelSlugs);
        SearchResult result = searchQueryService.search(new SearchQuery(
                keyword,
                namespaceId,
                scope,
                sortBy,
                page,
                size,
            normalizedLabelSlugs,
            labelMode,
            includeFacets
        ));
        List<SkillSummaryResponse> pageItems = mapVisibleSkillSummaries(result.skillIds());
        SearchFacetResponse facets = includeFacets
            ? new SearchFacetResponse(new LabelFacetGroupResponse(labelMode, mapLabelFacets(result.labelFacets())))
            : SearchFacetResponse.empty();
        AppliedFiltersResponse appliedFilters = new AppliedFiltersResponse(normalizedLabelSlugs, labelMode, namespaceSlug, sortBy);
        return new SearchResponse(pageItems, result.total(), page, size, facets, appliedFilters);
    }

    private List<String> normalizeLabelSlugs(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }
        return labelSlugs.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizeLabelMode(String labelMode) {
        if (labelMode == null || labelMode.isBlank()) {
            return "any";
        }
        String normalized = labelMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"any".equals(normalized) && !"all".equals(normalized)) {
            throw new DomainBadRequestException("search.label_mode.invalid", normalized);
        }
        return normalized;
    }

    private List<LabelFacetItemResponse> mapLabelFacets(List<SearchResult.LabelFacet> labelFacets) {
        if (labelFacets == null || labelFacets.isEmpty()) {
            return List.of();
        }

        Map<String, LabelDefinition> definitionsBySlug = labelDefinitionService.listVisibleFilters().stream()
                .collect(Collectors.toMap(
                        label -> label.getSlug().toLowerCase(java.util.Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<Long, java.util.List<com.iflytek.skillhub.domain.label.LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(
                        definitionsBySlug.values().stream().map(LabelDefinition::getId).distinct().toList());

        return labelFacets.stream()
                .map(facet -> {
                    LabelDefinition labelDefinition = definitionsBySlug.get(facet.slug().toLowerCase(java.util.Locale.ROOT));
                    String displayName = labelDefinition != null
                            ? labelLocalizationService.resolveDisplayName(
                                    labelDefinition.getSlug(),
                                    translationsByLabelId.getOrDefault(labelDefinition.getId(), List.of()))
                            : facet.slug();
                    String type = labelDefinition != null ? labelDefinition.getType().name() : facet.type();
                    return new LabelFacetItemResponse(facet.slug(), displayName, facet.count(), facet.selected(), type);
                })
                .toList();
    }

    private List<SkillSummaryResponse> mapVisibleSkillSummaries(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }

        List<Skill> matchedSkills = skillRepository.findByIdIn(skillIds);
        Map<Long, Skill> skillsById = matchedSkills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = matchedSkills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, String> namespaceSlugsById = namespacesById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSlug()));
        Map<Long, SkillLifecycleProjectionService.Projection> projectionsBySkillId =
                skillLifecycleProjectionService.projectPublishedSummaries(matchedSkills);

        return skillIds.stream()
                .map(skillsById::get)
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(skill, namespaceSlugsById, projectionsBySkillId.get(skill.getId())))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, String> namespaceSlugsById,
            SkillLifecycleProjectionService.Projection projection) {
        String namespaceSlug = namespaceSlugsById.get(skill.getNamespaceId());

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                namespaceSlug,
                skill.getUpdatedAt(),
                false,
                toLifecycleVersion(projection.headlineVersion()),
                toLifecycleVersion(projection.publishedVersion()),
                toLifecycleVersion(projection.ownerPreviewVersion()),
                projection.resolutionMode().name()
        );
    }

    private com.iflytek.skillhub.dto.SkillLifecycleVersionResponse toLifecycleVersion(
            SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(
                projection.id(),
                projection.version(),
                projection.status()
        );
    }

}
