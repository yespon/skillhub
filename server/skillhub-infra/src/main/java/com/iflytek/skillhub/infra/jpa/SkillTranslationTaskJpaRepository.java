package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillTranslationTask;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Override
    default List<SkillTranslationTask> findProcessableTasks(Instant now, int limit) {
        return findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                SkillTranslationTaskStatus.PENDING,
                now,
                PageRequest.of(0, limit)
        );
    }
}