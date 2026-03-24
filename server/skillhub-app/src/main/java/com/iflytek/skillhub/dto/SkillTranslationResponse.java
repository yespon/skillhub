package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillTranslationResponse(
        String locale,
        String displayName,
        String sourceType,
        Instant updatedAt
) {
}