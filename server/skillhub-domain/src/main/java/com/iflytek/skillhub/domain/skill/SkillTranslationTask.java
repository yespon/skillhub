package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_translation_task")
public class SkillTranslationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "source_text", nullable = false, length = 200)
    private String sourceText;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SkillTranslationTaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillTranslationTask() {
    }

    public SkillTranslationTask(Long skillId, String locale, String sourceText, String sourceHash) {
        this.skillId = skillId;
        this.locale = locale;
        this.sourceText = sourceText;
        this.sourceHash = sourceHash;
        this.status = SkillTranslationTaskStatus.PENDING;
        this.nextAttemptAt = Instant.now(Clock.systemUTC());
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        if (status == null) {
            status = SkillTranslationTaskStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getLocale() {
        return locale;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public SkillTranslationTaskStatus getStatus() {
        return status;
    }

    public void setStatus(SkillTranslationTaskStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}