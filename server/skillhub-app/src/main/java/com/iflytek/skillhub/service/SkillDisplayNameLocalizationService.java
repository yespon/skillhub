package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SkillDisplayNameLocalizationService {

    private final SkillTranslationRepository skillTranslationRepository;

    public SkillDisplayNameLocalizationService(SkillTranslationRepository skillTranslationRepository) {
        this.skillTranslationRepository = skillTranslationRepository;
    }

    public String resolveDisplayName(Long skillId, String canonicalDisplayName) {
        if (skillId == null) {
            return fallbackDisplayName(canonicalDisplayName);
        }
        return resolveDisplayName(
                skillTranslationRepository.findBySkillId(skillId),
                canonicalDisplayName
        );
    }

    public Map<Long, String> resolveDisplayNames(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return Map.of();
        }
        List<Long> skillIds = skills.stream()
                .map(Skill::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<SkillTranslation>> translationsBySkillId = skillTranslationRepository.findBySkillIdIn(skillIds)
                .stream()
                .collect(Collectors.groupingBy(SkillTranslation::getSkillId));

        Map<Long, String> resolved = new LinkedHashMap<>();
        for (Skill skill : skills) {
            if (skill.getId() == null) {
                continue;
            }
            resolved.put(
                    skill.getId(),
                    resolveDisplayName(
                            translationsBySkillId.getOrDefault(skill.getId(), List.of()),
                            skill.getDisplayName()
                    )
            );
        }
        return resolved;
    }

    public String resolveDisplayName(List<SkillTranslation> translations, String canonicalDisplayName) {
        Map<String, String> values = translations.stream()
                .collect(Collectors.toMap(
                        translation -> normalizeLocale(translation.getLocale()),
                        SkillTranslation::getDisplayName,
                        (left, right) -> left
                ));
        for (String candidate : localeCandidates()) {
            String value = values.get(candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallbackDisplayName(canonicalDisplayName);
    }

    private List<String> localeCandidates() {
        Locale locale = LocaleContextHolder.getLocale();
        String languageTag = normalizeLocale(locale.toLanguageTag());
        String language = normalizeLocale(locale.getLanguage());

        if (language.startsWith("zh")) {
            return List.of(languageTag, language, "zh-cn", "zh", "en").stream()
                .filter(candidate -> !candidate.isBlank())
                .distinct()
                .toList();
        }

        return List.of(languageTag, language, "en").stream()
            .filter(candidate -> !candidate.isBlank())
            .distinct()
            .toList();
    }

    private String fallbackDisplayName(String canonicalDisplayName) {
        if (canonicalDisplayName == null) {
            return "";
        }
        return canonicalDisplayName;
    }

    private String normalizeLocale(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}