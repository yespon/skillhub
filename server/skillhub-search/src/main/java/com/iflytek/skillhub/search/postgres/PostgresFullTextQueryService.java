package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL-backed implementation of {@link SearchQueryService}.
 *
 * <p>The query pipeline combines structured visibility filters, full-text
 * ranking, and an optional semantic re-ranking pass over a bounded candidate
 * set.
 *
 * <p>This class is a deliberate direct-persistence exception. Search ranking,
 * FTS predicates, and candidate-window tuning are storage-engine-specific
 * concerns, so keeping the native SQL close to the search adapter is clearer
 * than forcing that logic through domain repository ports or generic
 * controller-facing query repositories.
 */
@Service
public class PostgresFullTextQueryService implements SearchQueryService {
    private static final int MAX_QUERY_TERMS = 8;
    private static final int SHORT_PREFIX_LENGTH = 2;
    private static final String TITLE_VECTOR_SQL = "to_tsvector('simple', coalesce(title, ''))";
    private static final String TITLE_SQL = "LOWER(title)";

    private final EntityManager entityManager;
    private final SkillSearchDocumentJpaRepository searchDocumentRepository;
    private final SearchEmbeddingService searchEmbeddingService;
    private final SearchTextTokenizer searchTextTokenizer;
    private final boolean semanticEnabled;
    private final double semanticWeight;
    private final int candidateMultiplier;
    private final int maxCandidates;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this(entityManager, null, null, new SearchTextTokenizer(), false, 0.35D, 8, 120);
    }

    @Autowired
    public PostgresFullTextQueryService(EntityManager entityManager,
                                        SkillSearchDocumentJpaRepository searchDocumentRepository,
                                        SearchEmbeddingService searchEmbeddingService,
                                        SearchTextTokenizer searchTextTokenizer,
                                        @Value("${skillhub.search.semantic.enabled:true}") boolean semanticEnabled,
                                        @Value("${skillhub.search.semantic.weight:0.35}") double semanticWeight,
                                        @Value("${skillhub.search.semantic.candidate-multiplier:8}") int candidateMultiplier,
                                        @Value("${skillhub.search.semantic.max-candidates:120}") int maxCandidates) {
        this.entityManager = entityManager;
        this.searchDocumentRepository = searchDocumentRepository;
        this.searchEmbeddingService = searchEmbeddingService;
        this.searchTextTokenizer = searchTextTokenizer;
        this.semanticEnabled = semanticEnabled;
        this.semanticWeight = semanticWeight;
        this.candidateMultiplier = candidateMultiplier;
        this.maxCandidates = maxCandidates;
    }

    /**
     * Executes a search query against the denormalized search document table
     * and optionally re-ranks candidates using embeddings.
     */
    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        String tsQuery = buildPrefixTsQuery(normalizedKeyword);
        boolean hasKeyword = normalizedKeyword != null;
        boolean hasTsQuery = tsQuery != null;
        boolean useRelevanceOrdering = "relevance".equals(query.sortBy()) && hasKeyword;
        boolean useShortPrefixTitleSearch = hasTsQuery && isShortAsciiPrefixSearch(normalizedKeyword);
        boolean useSemanticRerank = semanticEnabled
                && hasKeyword
                && "relevance".equals(query.sortBy())
                && searchDocumentRepository != null
                && searchEmbeddingService != null;
        int requestedOffset = query.page() * query.size();
        if (useSemanticRerank && requestedOffset + query.size() > maxCandidates) {
            useSemanticRerank = false;
        }
        int sqlLimit = query.size();
        int sqlOffset = requestedOffset;
        if (useSemanticRerank) {
            sqlLimit = Math.min(Math.max((query.page() + 1) * query.size() * candidateMultiplier, query.size() * candidateMultiplier), maxCandidates);
            sqlOffset = 0;
        }
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();
        Set<Long> adminNamespaceIds = query.visibilityScope().adminNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().adminNamespaceIds();
        boolean platformWideAccess = query.visibilityScope().platformWideAccess();

        String baseSql = buildBaseSql(query, hasKeyword, hasTsQuery, useShortPrefixTitleSearch);
        String searchSql = "SELECT d.skill_id " + baseSql + buildOrderByClause(query.sortBy(), useRelevanceOrdering, useShortPrefixTitleSearch, hasTsQuery)
                + "LIMIT :limit OFFSET :offset";

        Query nativeQuery = entityManager.createNativeQuery(searchSql);
        applyCommonParameters(nativeQuery, query, memberNamespaceIds, adminNamespaceIds, platformWideAccess, normalizedKeyword, tsQuery,
                hasKeyword, useRelevanceOrdering, sqlLimit, sqlOffset, true);

        @SuppressWarnings("unchecked")
        List<Long> skillIds = (List<Long>) nativeQuery.getResultList().stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        String countSql = "SELECT COUNT(*) " + baseSql;
        Query countQuery = entityManager.createNativeQuery(countSql);
        applyCommonParameters(countQuery, query, memberNamespaceIds, adminNamespaceIds, platformWideAccess, normalizedKeyword, tsQuery,
                hasKeyword, false, null, null, false);

        long total = ((Number) countQuery.getSingleResult()).longValue();

        if (useSemanticRerank && !skillIds.isEmpty()) {
            skillIds = rerankBySemanticSimilarity(skillIds, normalizedKeyword, requestedOffset, query.size());
        }

        List<SearchResult.LabelFacet> labelFacets = query.includeFacets() && !skillIds.isEmpty()
            ? queryLabelFacets(baseSql, query, memberNamespaceIds, adminNamespaceIds, platformWideAccess, normalizedKeyword, tsQuery, hasKeyword)
                : List.of();

        return new SearchResult(skillIds, total, query.page(), query.size(), labelFacets);
    }

    private String buildBaseSql(SearchQuery query,
                                boolean hasKeyword,
                                boolean hasTsQuery,
                                boolean useShortPrefixTitleSearch) {
        StringBuilder sql = new StringBuilder();
        sql.append("FROM skill_search_document d ");
        sql.append("JOIN skill s ON s.id = d.skill_id ");
        sql.append("JOIN namespace n ON n.id = d.namespace_id ");
        sql.append("WHERE 1=1 ");

        appendVisibilityFilters(sql, query);
        appendStatusFilters(sql, query);
        appendNamespaceFilter(sql, query);
        appendLabelFilter(sql, query);
        appendKeywordFilter(sql, hasKeyword, hasTsQuery, useShortPrefixTitleSearch);

        return sql.toString();
    }

    private void appendVisibilityFilters(StringBuilder sql, SearchQuery query) {
        sql.append("AND (d.visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespace_id IN :memberNamespaceIds) ");
        }
        sql.append(") ");
    }

    private void appendStatusFilters(StringBuilder sql, SearchQuery query) {
        sql.append("AND d.status = 'ACTIVE' ");
        sql.append("AND s.status = 'ACTIVE' ");
        sql.append("AND s.hidden = FALSE ");
        sql.append("AND (n.status <> 'ARCHIVED' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR d.namespace_id IN :memberNamespaceIds ");
        }
        sql.append(") ");
    }

    private void appendNamespaceFilter(StringBuilder sql, SearchQuery query) {
        if (query.namespaceId() != null) {
            sql.append("AND d.namespace_id = :namespaceId ");
        }
    }

    private void appendLabelFilter(StringBuilder sql, SearchQuery query) {
        if (query.labelSlugs() == null || query.labelSlugs().isEmpty()) {
            return;
        }

        sql.append("AND d.skill_id IN (");
        sql.append("SELECT sl.skill_id FROM skill_label sl ");
        sql.append("JOIN label_definition ld ON ld.id = sl.label_id ");
        sql.append("WHERE ld.slug IN :labelSlugs ");
        if ("all".equals(query.labelMode())) {
            sql.append("GROUP BY sl.skill_id HAVING COUNT(DISTINCT ld.slug) = :labelCount ");
        }
        sql.append(") ");
    }

    private void appendKeywordFilter(StringBuilder sql,
                                     boolean hasKeyword,
                                     boolean hasTsQuery,
                                     boolean useShortPrefixTitleSearch) {
        if (!hasKeyword) {
            return;
        }
        sql.append("AND (");
        if (hasTsQuery) {
            if (useShortPrefixTitleSearch) {
                sql.append(TITLE_VECTOR_SQL).append(" @@ to_tsquery('simple', :tsQuery) ");
            } else {
                sql.append("d.search_vector @@ to_tsquery('simple', :tsQuery) ");
            }
            sql.append(" OR ");
        }
        sql.append(TITLE_SQL).append(" LIKE :titleLike");
        sql.append(") ");
    }

    private String buildOrderByClause(String sortBy,
                                      boolean useRelevanceOrdering,
                                      boolean useShortPrefixTitleSearch,
                                      boolean hasTsQuery) {
        StringBuilder sql = new StringBuilder();
        if ("downloads".equals(sortBy)) {
            sql.append("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("rating".equals(sortBy)) {
            sql.append("ORDER BY s.rating_avg DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("newest".equals(sortBy)) {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        } else if (useRelevanceOrdering) {
            sql.append("ORDER BY CASE ");
            sql.append("WHEN ").append(TITLE_SQL).append(" = :titleExact THEN 4 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titlePrefix THEN 3 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titleLike THEN 2 ");
            sql.append("ELSE 1 END DESC, ");
            if (useShortPrefixTitleSearch) {
                sql.append("ts_rank_cd(").append(TITLE_VECTOR_SQL)
                        .append(", to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else if (hasTsQuery) {
                sql.append("ts_rank_cd(d.search_vector, to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else {
                sql.append("d.updated_at DESC, d.skill_id DESC ");
            }
        } else {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        }
        return sql.toString();
    }

    private void applyCommonParameters(Query queryObject,
                                       SearchQuery query,
                                       Set<Long> memberNamespaceIds,
                                       Set<Long> adminNamespaceIds,
                                       boolean platformWideAccess,
                                       String normalizedKeyword,
                                       String tsQuery,
                                       boolean hasKeyword,
                                       boolean includeRelevanceOrderingParameters,
                                       Integer limit,
                                       Integer offset,
                                       boolean includePagination) {
        if (query.visibilityScope().userId() != null) {
            queryObject.setParameter("memberNamespaceIds", memberNamespaceIds);
        }

        if (query.namespaceId() != null) {
            queryObject.setParameter("namespaceId", query.namespaceId());
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            queryObject.setParameter("labelSlugs", query.labelSlugs());
            if ("all".equals(query.labelMode())) {
                queryObject.setParameter("labelCount", (long) query.labelSlugs().size());
            }
        }

        if (hasKeyword) {
            if (tsQuery != null) {
                queryObject.setParameter("tsQuery", tsQuery);
            }
            if (includeRelevanceOrderingParameters) {
                queryObject.setParameter("titleExact", normalizedKeyword.toLowerCase(Locale.ROOT));
                queryObject.setParameter("titlePrefix", normalizedKeyword.toLowerCase(Locale.ROOT) + "%");
            }
            queryObject.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%");
        }

        if (includePagination) {
            queryObject.setParameter("limit", limit);
            queryObject.setParameter("offset", offset);
        }
    }

    private List<SearchResult.LabelFacet> queryLabelFacets(String baseSql,
                                                           SearchQuery query,
                                                           Set<Long> memberNamespaceIds,
                                                           Set<Long> adminNamespaceIds,
                                                           boolean platformWideAccess,
                                                           String normalizedKeyword,
                                                           String tsQuery,
                                                           boolean hasKeyword) {
        String facetSql = "WITH matched_skills AS (SELECT DISTINCT d.skill_id " + baseSql + ") "
                + "SELECT ld.slug, COUNT(DISTINCT ms.skill_id) AS skill_count, ld.type "
                + "FROM matched_skills ms "
                + "JOIN skill_label sl ON sl.skill_id = ms.skill_id "
                + "JOIN label_definition ld ON ld.id = sl.label_id "
                + "WHERE ld.visible_in_filter = TRUE "
                + "GROUP BY ld.slug, ld.type, ld.sort_order "
                + "ORDER BY ld.sort_order ASC, ld.slug ASC";
        Query facetQuery = entityManager.createNativeQuery(facetSql);
        applyCommonParameters(facetQuery, query, memberNamespaceIds, adminNamespaceIds, platformWideAccess, normalizedKeyword, tsQuery,
                hasKeyword, false, null, null, false);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = facetQuery.getResultList();
        Set<String> selectedSlugs = query.labelSlugs() == null
            ? Set.of()
            : query.labelSlugs().stream().collect(java.util.stream.Collectors.toSet());

        return rows.stream()
                .map(row -> new SearchResult.LabelFacet(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        row[2] != null ? row[2].toString() : null,
                        selectedSlugs.contains((String) row[0])
                ))
                .toList();
    }

    private List<Long> rerankBySemanticSimilarity(List<Long> candidateSkillIds,
                                                  String normalizedKeyword,
                                                  int requestedOffset,
                                                  int pageSize) {
        Map<Long, SkillSearchDocumentEntity> documentsBySkillId = new HashMap<>();
        for (SkillSearchDocumentEntity entity : searchDocumentRepository.findBySkillIdIn(candidateSkillIds)) {
            documentsBySkillId.put(entity.getSkillId(), entity);
        }

        int totalCandidates = Math.max(candidateSkillIds.size(), 1);
        List<RankedSkill> rankedSkills = new java.util.ArrayList<>(candidateSkillIds.size());
        for (int index = 0; index < candidateSkillIds.size(); index++) {
            Long skillId = candidateSkillIds.get(index);
            SkillSearchDocumentEntity entity = documentsBySkillId.get(skillId);
            double baseScore = 1D - (index / (double) totalCandidates);
            double semanticScore = computeSemanticScore(normalizedKeyword, entity);
            double combinedScore = (baseScore * (1D - semanticWeight)) + (semanticScore * semanticWeight);
            rankedSkills.add(new RankedSkill(skillId, combinedScore));
        }

        return rankedSkills.stream()
                .sorted(Comparator.comparingDouble(RankedSkill::score).reversed())
                .skip(requestedOffset)
                .limit(pageSize)
                .map(RankedSkill::skillId)
                .toList();
    }

    private double computeSemanticScore(String normalizedKeyword, SkillSearchDocumentEntity entity) {
        if (entity == null) {
            return 0D;
        }
        String serializedVector = entity.getSemanticVector();
        if (serializedVector == null || serializedVector.isBlank()) {
            serializedVector = searchEmbeddingService.embed(String.join("\n",
                    safe(entity.getTitle()),
                    safe(entity.getSummary()),
                    safe(entity.getKeywords()),
                    safe(entity.getSearchText())));
        }
        return searchEmbeddingService.similarity(normalizedKeyword, serializedVector);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String buildPrefixTsQuery(String keyword) {
        if (keyword == null) {
            return null;
        }

        List<String> terms = searchTextTokenizer.tokenizeForQuery(keyword).stream()
                .limit(MAX_QUERY_TERMS)
                .toList();

        if (terms.isEmpty()) {
            return null;
        }

        List<String> tsQueryTerms = terms.stream()
                .filter(this::isTsQueryCompatibleTerm)
                .toList();

        if (tsQueryTerms.isEmpty()) {
            return null;
        }

        return tsQueryTerms.stream()
                .map(term -> usePrefixMatch(term) ? term + ":*" : term)
                .reduce((left, right) -> left + " & " + right)
                .orElse(null);
    }

    private boolean isTsQueryCompatibleTerm(String term) {
        return term.chars().anyMatch(ch -> Character.isLetter(ch) || Character.isIdeographic(ch) || ch == '_');
    }

    private boolean usePrefixMatch(String term) {
        return term.chars().allMatch(ch -> ch < 128) && term.chars().anyMatch(Character::isLetter);
    }

    private boolean isShortAsciiPrefixSearch(String keyword) {
        if (keyword == null || keyword.length() > SHORT_PREFIX_LENGTH) {
            return false;
        }
        List<String> terms = searchTextTokenizer.tokenizeForQuery(keyword);
        return !terms.isEmpty() && terms.stream().allMatch(this::usePrefixMatch);
    }

    private record RankedSkill(Long skillId, double score) {
    }
}
