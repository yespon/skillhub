package com.iflytek.skillhub.domain.skill;

import java.time.Instant;
import java.util.List;

public interface SkillTranslationTaskRepository {
    <S extends SkillTranslationTask> S save(S task);
    List<SkillTranslationTask> findProcessableTasks(Instant now, int limit);
    boolean existsBySkillIdAndLocaleAndSourceHashAndStatusIn(Long skillId,
                                                             String locale,
                                                             String sourceHash,
                                                             List<SkillTranslationTaskStatus> statuses);
    List<SkillTranslationTask> findBySkillIdAndLocaleAndStatusIn(Long skillId,
                                                                 String locale,
                                                                 List<SkillTranslationTaskStatus> statuses);
}