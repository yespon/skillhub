package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillTranslationTask;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillTranslationTaskJpaRepository extends JpaRepository<SkillTranslationTask, Long>, SkillTranslationTaskRepository {

    List<SkillTranslationTask> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            SkillTranslationTaskStatus status,
            Instant now,
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsBySkillIdAndLocaleAndSourceHashAndStatusIn(Long skillId,
                                                             String locale,
                                                             String sourceHash,
                                                             List<SkillTranslationTaskStatus> statuses);

    List<SkillTranslationTask> findBySkillIdAndLocaleAndStatusIn(Long skillId,
                                                                 String locale,
                                                                 List<SkillTranslationTaskStatus> statuses);

    List<SkillTranslationTask> findByStatusAndLockedBy(SkillTranslationTaskStatus status, String lockedBy);
    @Override
    default List<SkillTranslationTask> findProcessableTasks(Instant now, int limit) {
        return findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                SkillTranslationTaskStatus.PENDING,
                now,
                PageRequest.of(0, limit)
        );
    }

    @Modifying
    @Query(value = """
            UPDATE skill_translation_task
            SET status = 'RUNNING',
                locked_at = :now,
                locked_by = :lockedBy,
                attempt_count = attempt_count + 1,
                updated_at = :now
            WHERE id IN (
                SELECT id FROM skill_translation_task
                WHERE status = 'PENDING'
                  AND next_attempt_at <= :now
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int claimProcessableTasksNative(@Param("now") Instant now,
                                    @Param("limit") int limit,
                                    @Param("lockedBy") String lockedBy);

    @Override
    default int claimProcessableTasks(Instant now, int limit, String lockedBy) {
        return claimProcessableTasksNative(now, limit, lockedBy);
    }
}