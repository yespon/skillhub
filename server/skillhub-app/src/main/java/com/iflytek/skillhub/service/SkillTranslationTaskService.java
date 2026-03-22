package com.iflytek.skillhub.service;

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
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillTranslationTaskService {

    public static final String ZH_CN_LOCALE = "zh-cn";
    private static final Logger log = LoggerFactory.getLogger(SkillTranslationTaskService.class);
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final int MAX_ATTEMPTS = 3;

    private final SkillRepository skillRepository;
    private final SkillTranslationRepository skillTranslationRepository;
    private final SkillTranslationTaskRepository skillTranslationTaskRepository;
    private final SkillDisplayNameTranslationProvider translationProvider;
    private final LabelSearchSyncService labelSearchSyncService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final boolean autoTranslationEnabled;
    private final int batchSize;
    private final String workerId;

    public SkillTranslationTaskService(SkillRepository skillRepository,
                                       SkillTranslationRepository skillTranslationRepository,
                                       SkillTranslationTaskRepository skillTranslationTaskRepository,
                                       SkillDisplayNameTranslationProvider translationProvider,
                                       LabelSearchSyncService labelSearchSyncService,
                                       AuditLogService auditLogService,
                                       Clock clock,
                                       @Value("${skillhub.translation.auto.enabled:false}") boolean autoTranslationEnabled,
                                       @Value("${skillhub.translation.auto.batch-size:10}") int batchSize) {
        this.skillRepository = skillRepository;
        this.skillTranslationRepository = skillTranslationRepository;
        this.skillTranslationTaskRepository = skillTranslationTaskRepository;
        this.translationProvider = translationProvider;
        this.labelSearchSyncService = labelSearchSyncService;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.autoTranslationEnabled = autoTranslationEnabled;
        this.batchSize = batchSize;
        this.workerId = UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional
    public void maybeEnqueueForSkill(Long skillId) {
        if (!autoTranslationEnabled || skillId == null) {
            return;
        }
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null) {
            return;
        }
        String canonicalDisplayName = normalizeDisplayName(skill.getDisplayName());
        if (canonicalDisplayName == null || containsHan(canonicalDisplayName)) {
            return;
        }

        Optional<SkillTranslation> existingTranslation = skillTranslationRepository.findBySkillIdAndLocale(skillId, ZH_CN_LOCALE);
        if (existingTranslation.isPresent()) {
            SkillTranslation translation = existingTranslation.get();
            if (translation.getSourceType() == SkillTranslationSourceType.USER) {
                return;
            }
            if (translation.getSourceHash() != null && translation.getSourceHash().equals(hash(canonicalDisplayName))) {
                return;
            }
        }

        String sourceHash = hash(canonicalDisplayName);
        if (skillTranslationTaskRepository.existsBySkillIdAndLocaleAndSourceHashAndStatusIn(
                skillId,
                ZH_CN_LOCALE,
                sourceHash,
                List.of(SkillTranslationTaskStatus.PENDING, SkillTranslationTaskStatus.RUNNING))) {
            return;
        }

        skillTranslationTaskRepository.save(new SkillTranslationTask(skillId, ZH_CN_LOCALE, canonicalDisplayName, sourceHash));
    }

    @Transactional
    public void cancelPendingTasks(Long skillId, String locale) {
        if (skillId == null || locale == null) {
            return;
        }
        List<SkillTranslationTask> tasks = skillTranslationTaskRepository.findBySkillIdAndLocaleAndStatusIn(
                skillId,
                locale.trim().toLowerCase(java.util.Locale.ROOT),
                List.of(SkillTranslationTaskStatus.PENDING, SkillTranslationTaskStatus.RUNNING)
        );
        Instant now = Instant.now(clock);
        for (SkillTranslationTask task : tasks) {
            task.setStatus(SkillTranslationTaskStatus.SKIPPED);
            task.setLastError("Cancelled by manual translation update");
            task.setCompletedAt(now);
            task.setNextAttemptAt(now);
            skillTranslationTaskRepository.save(task);
        }
    }

    @Transactional
    public int processPendingTasks() {
        if (!autoTranslationEnabled) {
            return 0;
        }
        Instant now = Instant.now(clock);
        int claimed = skillTranslationTaskRepository.claimProcessableTasks(now, Math.max(batchSize, 1), workerId);
        if (claimed == 0) {
            return 0;
        }
        List<SkillTranslationTask> tasks = skillTranslationTaskRepository.findByStatusAndLockedBy(
                SkillTranslationTaskStatus.RUNNING, workerId);
        int processed = 0;
        for (SkillTranslationTask task : tasks) {
            processTask(task);
            processed++;
        }
        return processed;
    }

    private void processTask(SkillTranslationTask task) {
        try {
            Skill skill = skillRepository.findById(task.getSkillId()).orElse(null);
            if (skill == null) {
                completeTask(task, SkillTranslationTaskStatus.SKIPPED, "Skill not found");
                return;
            }

            String canonicalDisplayName = normalizeDisplayName(skill.getDisplayName());
            if (canonicalDisplayName == null || containsHan(canonicalDisplayName)) {
                completeTask(task, SkillTranslationTaskStatus.SKIPPED, "Canonical display name no longer requires translation");
                return;
            }
            if (!hash(canonicalDisplayName).equals(task.getSourceHash())) {
                completeTask(task, SkillTranslationTaskStatus.SKIPPED, "Source display name changed before processing");
                return;
            }

            Optional<SkillTranslation> existingTranslation = skillTranslationRepository.findBySkillIdAndLocale(task.getSkillId(), task.getLocale());
            if (existingTranslation.isPresent() && existingTranslation.get().getSourceType() == SkillTranslationSourceType.USER) {
                completeTask(task, SkillTranslationTaskStatus.SKIPPED, "Manual translation already exists");
                return;
            }

            Optional<String> translated = translationProvider.translateToZhCn(canonicalDisplayName)
                    .map(this::normalizeGeneratedDisplayName)
                    .filter(value -> value != null && !value.isBlank());
            if (translated.isEmpty()) {
                failOrSkipTask(task, "No translation provider result");
                return;
            }

            SkillTranslation translation = existingTranslation
                    .orElseGet(() -> new SkillTranslation(task.getSkillId(), task.getLocale(), translated.get()));
            translation.setDisplayName(translated.get());
            translation.setSourceType(SkillTranslationSourceType.MACHINE);
            translation.setSourceHash(task.getSourceHash());
            skillTranslationRepository.save(translation);

            completeTask(task, SkillTranslationTaskStatus.SUCCEEDED, null);
            labelSearchSyncService.rebuildSkill(task.getSkillId());
            auditLogService.record(
                    null,
                    "SKILL_TRANSLATION_AUTO_FILL",
                    "SKILL",
                    task.getSkillId(),
                    MDC.get("requestId"),
                    null,
                    null,
                    "{\"locale\":\"" + task.getLocale() + "\",\"sourceType\":\"MACHINE\"}"
            );
        } catch (RuntimeException ex) {
            log.error("Failed to process skill translation task {}", task.getId(), ex);
            failOrSkipTask(task, ex.getMessage());
        }
    }

    private void completeTask(SkillTranslationTask task, SkillTranslationTaskStatus status, String lastError) {
        Instant now = Instant.now(clock);
        task.setStatus(status);
        task.setLastError(lastError);
        task.setCompletedAt(now);
        task.setNextAttemptAt(now);
        skillTranslationTaskRepository.save(task);
    }

    private void failOrSkipTask(SkillTranslationTask task, String reason) {
        Instant now = Instant.now(clock);
        if (task.getAttemptCount() >= MAX_ATTEMPTS) {
            task.setStatus(SkillTranslationTaskStatus.FAILED);
            task.setCompletedAt(now);
        } else {
            task.setStatus(SkillTranslationTaskStatus.PENDING);
            task.setNextAttemptAt(now.plusSeconds((long) task.getAttemptCount() * 60));
        }
        task.setLastError(truncate(reason));
        skillTranslationTaskRepository.save(task);
    }

    private String normalizeGeneratedDisplayName(String displayName) {
        String normalized = normalizeDisplayName(displayName);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean containsHan(String value) {
        return HAN_PATTERN.matcher(value).find();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 support", e);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}