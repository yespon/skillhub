package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record LabelDefinitionResponse(
        String slug,
        String type,
        boolean visibleInFilter,
        int sortOrder,
        List<LabelTranslationResponse> translations,
        long usageCount,
        Instant createdAt
) {
    public LabelDefinitionResponse(String slug,
                                   String type,
                                   boolean visibleInFilter,
                                   int sortOrder,
                                   List<LabelTranslationResponse> translations,
                                   Instant createdAt) {
        this(slug, type, visibleInFilter, sortOrder, translations, 0L, createdAt);
    }
}
