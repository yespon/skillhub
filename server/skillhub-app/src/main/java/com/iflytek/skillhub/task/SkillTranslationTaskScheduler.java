package com.iflytek.skillhub.task;

import com.iflytek.skillhub.service.SkillTranslationTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SkillTranslationTaskScheduler {

    private final SkillTranslationTaskService skillTranslationTaskService;

    public SkillTranslationTaskScheduler(SkillTranslationTaskService skillTranslationTaskService) {
        this.skillTranslationTaskService = skillTranslationTaskService;
    }

    @Scheduled(fixedDelayString = "${skillhub.translation.auto.fixed-delay-ms:60000}")
    public void processPendingTasks() {
        skillTranslationTaskService.processPendingTasks();
    }
}