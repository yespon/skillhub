package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillTranslationRepository {
    List<SkillTranslation> findBySkillId(Long skillId);
    List<SkillTranslation> findBySkillIdIn(List<Long> skillIds);
    Optional<SkillTranslation> findBySkillIdAndLocale(Long skillId, String locale);
    <S extends SkillTranslation> S save(S skillTranslation);
    void delete(SkillTranslation skillTranslation);
}