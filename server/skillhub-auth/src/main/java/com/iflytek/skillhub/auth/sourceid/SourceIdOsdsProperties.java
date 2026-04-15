package com.iflytek.skillhub.auth.sourceid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configures optional OSDS organization lookup for SourceID users.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.sourceid.osds")
public class SourceIdOsdsProperties {

    private boolean enabled = false;
    private String baseUrl;
    private String staffByUserIdPath = "/staff/user-id/{userId}/data";
    private String sysid;
    private String accessKeySecret;
    private String signServerAuth;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getStaffByUserIdPath() {
        return staffByUserIdPath;
    }

    public void setStaffByUserIdPath(String staffByUserIdPath) {
        this.staffByUserIdPath = staffByUserIdPath;
    }

    public String getSysid() {
        return sysid;
    }

    public void setSysid(String sysid) {
        this.sysid = sysid;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getSignServerAuth() {
        return signServerAuth;
    }

    public void setSignServerAuth(String signServerAuth) {
        this.signServerAuth = signServerAuth;
    }
}