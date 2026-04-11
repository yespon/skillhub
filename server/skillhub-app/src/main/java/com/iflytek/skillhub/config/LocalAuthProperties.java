package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.local")
public class LocalAuthProperties {

    /**
     * Controls whether the local username/password entry is advertised to the UI.
     */
    private boolean showEntry = true;

    public boolean isShowEntry() {
        return showEntry;
    }

    public void setShowEntry(boolean showEntry) {
        this.showEntry = showEntry;
    }
}