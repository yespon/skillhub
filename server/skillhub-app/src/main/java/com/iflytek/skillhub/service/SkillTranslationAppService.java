package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslation;
import com.iflytek.skillhub.domain.skill.SkillTranslationRepository;
import com.iflytek.skillhub.domain.skill.SkillTranslationSourceType;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillTranslationRequest;
import com.iflytek.skillhub.dto.SkillTranslationResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SkillTranslationAppService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillTranslationRepository skillTranslationRepository;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;
    private final LabelSearchSyncService labelSearchSyncService;
    private final SkillTranslationTaskService skillTranslationTaskService;

    public SkillTranslationAppService(NamespaceRepository namespaceRepository,
                                      SkillRepository skillRepository,
                                      SkillTranslationRepository skillTranslationRepository,
                                      RbacService rbacService,
                                      AuditLogService auditLogService,
                                      LabelSearchSyncService labelSearchSyncService,
                                      SkillTranslationTaskService skillTranslationTaskService) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillTranslationRepository = skillTranslationRepository;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
        this.labelSearchSyncService = labelSearchSyncService;
        this.skillTranslationTaskService = skillTranslationTaskService;
    }

    public List<SkillTranslationResponse> listSkillTranslations(String namespaceSlug,
                                                                String skillSlug,
                                                                String userId,
                                                                Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = resolveManageableSkill(namespaceSlug, skillSlug, userId, userNsRoles);
        return skillTranslationRepository.findBySkillId(skill.getId()).stream()
                .map(this::toResponse)
                .sorted(java.util.Comparator.comparing(SkillTranslationResponse::locale))
                .toList();
    }

    @Transactional
    public SkillTranslationResponse upsertSkillTranslation(String namespaceSlug,
                                                           String skillSlug,
                                                           String locale,
                                                           SkillTranslationRequest request,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNsRoles,
                                                           AuditRequestContext auditContext) {
        Skill skill = resolveManageableSkill(namespaceSlug, skillSlug, userId, userNsRoles);
        String normalizedLocale = normalizeSupportedLocale(locale);
        String normalizedDisplayName = normalizeDisplayName(request != null ? request.displayName() : null);
        SkillTranslation translation = skillTranslationRepository.findBySkillIdAndLocale(skill.getId(), normalizedLocale)
                .orElseGet(() -> new SkillTranslation(skill.getId(), normalizedLocale, normalizedDisplayName));
        translation.setDisplayName(normalizedDisplayName);
        translation.setSourceType(SkillTranslationSourceType.USER);
        translation.setSourceHash(null);
        SkillTranslation saved = skillTranslationRepository.save(translation);
        skillTranslationTaskService.cancelPendingTasks(skill.getId(), normalizedLocale);
        afterCommit(() -> labelSearchSyncService.rebuildSkill(skill.getId()));
        recordAudit(
                "SKILL_TRANSLATION_UPSERT",
                userId,
                skill.getId(),
                auditContext,
                "{\"locale\":\"" + normalizedLocale + "\",\"sourceType\":\"USER\"}"
        );
        return toResponse(saved);
    }

    @Transactional
    public MessageResponse deleteSkillTranslation(String namespaceSlug,
                                                  String skillSlug,
                                                  String locale,
                                                  String userId,
                                                  Map<Long, NamespaceRole> userNsRoles,
                                                  AuditRequestContext auditContext) {
        Skill skill = resolveManageableSkill(namespaceSlug, skillSlug, userId, userNsRoles);
        String normalizedLocale = normalizeSupportedLocale(locale);
        SkillTranslation translation = skillTranslationRepository.findBySkillIdAndLocale(skill.getId(), normalizedLocale)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.translation.notFound", normalizedLocale));
        skillTranslationRepository.delete(translation);
        skillTranslationTaskService.cancelPendingTasks(skill.getId(), normalizedLocale);
        afterCommit(() -> labelSearchSyncService.rebuildSkill(skill.getId()));
        recordAudit(
                "SKILL_TRANSLATION_DELETE",
                userId,
                skill.getId(),
                auditContext,
                "{\"locale\":\"" + normalizedLocale + "\"}"
        );
        return new MessageResponse("Translation deleted");
    }

    private Skill resolveManageableSkill(String namespaceSlug,
                                         String skillSlug,
                                         String userId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug).stream()
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        Set<String> platformRoles = userId == null ? Set.of() : rbacService.getUserRoleCodes(userId);
        NamespaceRole namespaceRole = userNsRoles != null ? userNsRoles.get(skill.getNamespaceId()) : null;
        boolean canManage = platformRoles.contains("SUPER_ADMIN")
                || skill.getOwnerId().equals(userId)
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.skill.lifecycle.noPermission");
        }
        return skill;
    }

    private SkillTranslationResponse toResponse(SkillTranslation translation) {
        return new SkillTranslationResponse(
                translation.getLocale(),
                translation.getDisplayName(),
                translation.getSourceType().name(),
                translation.getUpdatedAt()
        );
    }

    private String normalizeSupportedLocale(String locale) {
        if (locale == null) {
            throw new DomainBadRequestException("error.skill.translation.locale.invalid");
        }
        String normalized = locale.trim().toLowerCase(Locale.ROOT);
        if (!SkillTranslationTaskService.ZH_CN_LOCALE.equals(normalized)) {
            throw new DomainBadRequestException("error.skill.translation.locale.unsupported", locale);
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            throw new DomainBadRequestException("error.skill.translation.displayName.required");
        }
        String normalized = displayName.trim();
        if (normalized.isBlank()) {
            throw new DomainBadRequestException("error.skill.translation.displayName.required");
        }
        if (normalized.length() > 200) {
            throw new DomainBadRequestException("error.skill.translation.displayName.tooLong", 200);
        }
        return normalized;
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "SKILL",
                targetId,
                MDC.get("requestId"),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}