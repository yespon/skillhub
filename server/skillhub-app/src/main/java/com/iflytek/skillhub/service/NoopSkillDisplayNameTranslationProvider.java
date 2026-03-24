package com.iflytek.skillhub.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoopSkillDisplayNameTranslationProvider implements SkillDisplayNameTranslationProvider {

    @Override
    public Optional<String> translateToZhCn(String displayName) {
        return Optional.empty();
    }
}