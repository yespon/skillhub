package com.iflytek.skillhub.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationSourceType;
import com.iflytek.skillhub.domain.skill.SkillTranslationTask;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationTaskStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillTranslationTaskServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final SkillTranslationRepository skillTranslationRepository = mock(SkillTranslationRepository.class);
    private final SkillTranslationTaskRepository skillTranslationTaskRepository = mock(SkillTranslationTaskRepository.class);
    private final SkillDisplayNameTranslationProvider translationProvider = mock(SkillDisplayNameTranslationProvider.class);
    private final LabelSearchSyncService labelSearchSyncService = mock(LabelSearchSyncService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneOffset.UTC);
    private final SkillTranslationTaskService service = new SkillTranslationTaskService(
            skillRepository,
            skillTranslationRepository,
            skillTranslationTaskRepository,
            translationProvider,
            labelSearchSyncService,
            auditLogService,
            clock,
            true,
            10
    );

    @Test
    void maybeEnqueueForSkill_skipsWhenManualTranslationExists() {
        Skill skill = skill(88L, "Demo Skill");
        SkillTranslation translation = new SkillTranslation(88L, "zh-cn", "演示技能");
        translation.setSourceType(SkillTranslationSourceType.USER);
        when(skillRepository.findById(88L)).thenReturn(Optional.of(skill));
        when(skillTranslationRepository.findBySkillIdAndLocale(88L, SkillTranslationTaskService.ZH_CN_LOCALE)).thenReturn(Optional.of(translation));

        service.maybeEnqueueForSkill(88L);

        verify(skillTranslationTaskRepository, never()).save(any(SkillTranslationTask.class));
    }

    @Test
    void processPendingTasks_persistsMachineTranslationAndRebuildsSearch() {
        Skill skill = skill(88L, "Demo Skill");
        SkillTranslationTask task = new SkillTranslationTask(88L, "zh-cn", "Demo Skill", sha256("Demo Skill"));
        ReflectionTestUtils.setField(task, "id", 100L);
        when(skillTranslationTaskRepository.findProcessableTasks(Instant.parse("2026-03-22T00:00:00Z"), 10)).thenReturn(List.of(task));
        when(skillRepository.findById(88L)).thenReturn(Optional.of(skill));
        when(skillTranslationRepository.findBySkillIdAndLocale(88L, "zh-cn")).thenReturn(Optional.empty());
        when(translationProvider.translateToZhCn("Demo Skill")).thenReturn(Optional.of("演示技能"));
        when(skillTranslationRepository.save(any(SkillTranslation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skillTranslationTaskRepository.save(any(SkillTranslationTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processPendingTasks();

        verify(skillTranslationRepository).save(any(SkillTranslation.class));
        verify(labelSearchSyncService).rebuildSkill(88L);
        verify(auditLogService).record(eq(null), eq("SKILL_TRANSLATION_AUTO_FILL"), eq("SKILL"), eq(88L), any(), eq(null), eq(null), eq("{\"locale\":\"zh-cn\",\"sourceType\":\"MACHINE\"}"));
    }

    private Skill skill(Long id, String displayName) {
        Skill skill = new Skill(20L, "demo-skill", "owner-1", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", id);
        skill.setDisplayName(displayName);
        return skill;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}