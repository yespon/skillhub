package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class SkillDisplayNameLocalizationServiceTest {

    private final SkillDisplayNameLocalizationService service = new SkillDisplayNameLocalizationService(
            mock(SkillTranslationRepository.class)
    );

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolveDisplayName_prefersChineseTranslationForChineseLocale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-CN"));

        String displayName = service.resolveDisplayName(
                List.of(
                        translation("en", "Demo Skill"),
                        translation("zh-cn", "演示技能")
                ),
                "Demo Skill"
        );

        assertThat(displayName).isEqualTo("演示技能");
    }

    @Test
    void resolveDisplayName_prefersEnglishTranslationForNonChineseLocale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("fr-FR"));

        String displayName = service.resolveDisplayName(
                List.of(
                        translation("zh-cn", "演示技能"),
                        translation("en", "Demo Skill EN")
                ),
                "Demo Skill"
        );

        assertThat(displayName).isEqualTo("Demo Skill EN");
    }

    @Test
    void resolveDisplayName_usesCanonicalNameInsteadOfChineseFallbackForNonChineseLocale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ja-JP"));

        String displayName = service.resolveDisplayName(
                List.of(translation("zh-cn", "演示技能")),
                "Demo Skill"
        );

        assertThat(displayName).isEqualTo("Demo Skill");
    }

    private SkillTranslation translation(String locale, String displayName) {
        return new SkillTranslation(88L, locale, displayName);
    }
}