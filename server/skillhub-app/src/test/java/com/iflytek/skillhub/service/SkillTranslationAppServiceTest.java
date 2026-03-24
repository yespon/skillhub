package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationSourceType;
import com.iflytek.skillhub.dto.SkillTranslationRequest;
import com.iflytek.skillhub.dto.SkillTranslationResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillTranslationAppServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final com.iflytek.skillhub.domain.skill.SkillRepository skillRepository = mock(com.iflytek.skillhub.domain.skill.SkillRepository.class);
    private final SkillTranslationRepository skillTranslationRepository = mock(SkillTranslationRepository.class);
    private final RbacService rbacService = mock(RbacService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final LabelSearchSyncService labelSearchSyncService = mock(LabelSearchSyncService.class);
    private final SkillTranslationTaskService skillTranslationTaskService = mock(SkillTranslationTaskService.class);
    private final SkillTranslationAppService service = new SkillTranslationAppService(
            namespaceRepository,
            skillRepository,
            skillTranslationRepository,
            rbacService,
            auditLogService,
            labelSearchSyncService,
            skillTranslationTaskService
    );

    @Test
    void upsertSkillTranslation_marksUserSourceAndRebuildsSearch() {
        Namespace namespace = namespace(20L, "team-a");
        Skill skill = skill(88L, 20L, "demo-skill", "owner-1");
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(20L, "demo-skill")).thenReturn(List.of(skill));
        when(skillTranslationRepository.findBySkillIdAndLocale(88L, "zh-cn")).thenReturn(Optional.empty());
        when(skillTranslationRepository.save(any(SkillTranslation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rbacService.getUserRoleCodes("owner-1")).thenReturn(Set.of("USER"));

        SkillTranslationResponse response = service.upsertSkillTranslation(
                "team-a",
                "demo-skill",
                "zh-CN",
                new SkillTranslationRequest("演示技能"),
                "owner-1",
                Map.of(20L, NamespaceRole.MEMBER),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(response.locale()).isEqualTo("zh-cn");
        assertThat(response.displayName()).isEqualTo("演示技能");
        assertThat(response.sourceType()).isEqualTo("USER");
        verify(skillTranslationTaskService).cancelPendingTasks(88L, "zh-cn");
        verify(labelSearchSyncService).rebuildSkill(88L);
        verify(auditLogService).record(eq("owner-1"), eq("SKILL_TRANSLATION_UPSERT"), eq("SKILL"), eq(88L), any(), eq("127.0.0.1"), eq("JUnit"), eq("{\"locale\":\"zh-cn\",\"sourceType\":\"USER\"}"));
    }

    @Test
    void listSkillTranslations_returnsPersistedTranslations() {
        Namespace namespace = namespace(20L, "team-a");
        Skill skill = skill(88L, 20L, "demo-skill", "owner-1");
        SkillTranslation translation = new SkillTranslation(88L, "zh-cn", "演示技能");
        translation.setSourceType(SkillTranslationSourceType.MACHINE);
        ReflectionTestUtils.setField(translation, "updatedAt", java.time.Instant.parse("2026-03-22T00:00:00Z"));
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(20L, "demo-skill")).thenReturn(List.of(skill));
        when(skillTranslationRepository.findBySkillId(88L)).thenReturn(List.of(translation));
        when(rbacService.getUserRoleCodes("owner-1")).thenReturn(Set.of("USER"));

        List<SkillTranslationResponse> responses = service.listSkillTranslations(
                "team-a",
                "demo-skill",
                "owner-1",
                Map.of(20L, NamespaceRole.MEMBER)
        );

        assertThat(responses).singleElement().satisfies(item -> {
            assertThat(item.locale()).isEqualTo("zh-cn");
            assertThat(item.displayName()).isEqualTo("演示技能");
            assertThat(item.sourceType()).isEqualTo("MACHINE");
        });
        verify(skillTranslationTaskService, never()).cancelPendingTasks(any(), any());
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        return namespace;
    }

    private Skill skill(Long id, Long namespaceId, String slug, String ownerId) {
        Skill skill = new Skill(namespaceId, slug, ownerId, com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }
}