package com.iflytek.skillhub.domain.skill;

import java.time.Instant;
import java.util.List;

public interface SkillTranslationTaskRepository {
    <S extends SkillTranslationTask> S save(S task);
    List<SkillTranslationTask> findProcessableTasks(Instant now, int limit);
    /**
     * Atomically claims up to {@code limit} processable tasks in a single
     * operation, setting their status to RUNNING, locked_at, and locked_by.
     * Uses {@code FOR UPDATE SKIP LOCKED} to prevent duplicate processing
     * across concurrent workers or application instances.
     */
    int claimProcessableTasks(Instant now, int limit, String lockedBy);
    boolean existsBySkillIdAndLocaleAndSourceHashAndStatusIn(Long skillId,
                                                             String locale,
                                                             String sourceHash,
                                                             List<SkillTranslationTaskStatus> statuses);
    List<SkillTranslationTask> findBySkillIdAndLocaleAndStatusIn(Long skillId,
                                                                 String locale,
                                                                 List<SkillTranslationTaskStatus> statuses);
    List<SkillTranslationTask> findByStatusAndLockedBy(SkillTranslationTaskStatus status, String lockedBy);
}