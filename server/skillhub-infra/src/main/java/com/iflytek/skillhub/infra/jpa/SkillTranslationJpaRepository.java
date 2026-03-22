package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillTranslationJpaRepository extends JpaRepository<SkillTranslation, Long>, SkillTranslationRepository {
    List<SkillTranslation> findBySkillId(Long skillId);
    List<SkillTranslation> findBySkillIdIn(List<Long> skillIds);
    Optional<SkillTranslation> findBySkillIdAndLocale(Long skillId, String locale);
}