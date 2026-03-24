package com.iflytek.skillhub.service;

import java.util.Optional;

public interface SkillDisplayNameTranslationProvider {
    Optional<String> translateToZhCn(String displayName);
}