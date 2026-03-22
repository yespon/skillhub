package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.LabelUsageCount;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillLabelJpaRepository extends JpaRepository<SkillLabel, Long>, SkillLabelRepository {
    List<SkillLabel> findBySkillId(Long skillId);
    List<SkillLabel> findBySkillIdIn(List<Long> skillIds);
    List<SkillLabel> findByLabelId(Long labelId);
    Optional<SkillLabel> findBySkillIdAndLabelId(Long skillId, Long labelId);
    long countBySkillId(Long skillId);

    @Query("""
            select new com.iflytek.skillhub.domain.label.LabelUsageCount(sl.labelId, count(distinct sl.skillId))
            from SkillLabel sl
            where sl.labelId in :labelIds
            group by sl.labelId
            """)
    List<LabelUsageCount> countDistinctSkillsByLabelIds(@Param("labelIds") List<Long> labelIds);
}
