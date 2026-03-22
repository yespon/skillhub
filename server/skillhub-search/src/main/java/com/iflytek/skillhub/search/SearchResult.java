package com.iflytek.skillhub.search;

import java.util.List;

/**
 * Compact search response containing matching skill identifiers and pagination metadata.
 */
public record SearchResult(
        List<Long> skillIds,
        long total,
        int page,
        int size,
        List<LabelFacet> labelFacets
) {
    public SearchResult(List<Long> skillIds, long total, int page, int size) {
        this(skillIds, total, page, size, List.of());
    }

    public record LabelFacet(
            String slug,
            long count,
            String type,
            boolean selected
    ) {
    }
}
