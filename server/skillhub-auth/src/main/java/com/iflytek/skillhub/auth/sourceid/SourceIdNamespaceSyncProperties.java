package com.iflytek.skillhub.auth.sourceid;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configures login-time namespace auto-membership reconciliation for SourceID users.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.sourceid.namespace-sync")
public class SourceIdNamespaceSyncProperties {

    private boolean enabled = false;
    private String providerCode = "sourceid";
    private String attributeRootKey = "attributes";
    private String activeStatusKey = "active";
    private List<String> activeStatusValues = new ArrayList<>(List.of("true"));
    private List<Mapping> mappings = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getAttributeRootKey() {
        return attributeRootKey;
    }

    public void setAttributeRootKey(String attributeRootKey) {
        this.attributeRootKey = attributeRootKey;
    }

    public String getActiveStatusKey() {
        return activeStatusKey;
    }

    public void setActiveStatusKey(String activeStatusKey) {
        this.activeStatusKey = activeStatusKey;
    }

    public List<String> getActiveStatusValues() {
        return activeStatusValues;
    }

    public void setActiveStatusValues(List<String> activeStatusValues) {
        this.activeStatusValues = activeStatusValues != null ? new ArrayList<>(activeStatusValues) : new ArrayList<>();
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings != null ? new ArrayList<>(mappings) : new ArrayList<>();
    }

    public static class Mapping {
        private String attributeKey;
        private String attributeValue;
        private List<String> attributeValues = new ArrayList<>();
        private String namespaceSlug;
        private NamespaceRole role = NamespaceRole.MEMBER;

        public String getAttributeKey() {
            return attributeKey;
        }

        public void setAttributeKey(String attributeKey) {
            this.attributeKey = attributeKey;
        }

        public String getAttributeValue() {
            return attributeValue;
        }

        public void setAttributeValue(String attributeValue) {
            this.attributeValue = attributeValue;
        }

        public List<String> getAttributeValues() {
            if (attributeValues.isEmpty() && attributeValue != null) {
                return List.of(attributeValue);
            }
            return attributeValues;
        }

        public void setAttributeValues(List<String> attributeValues) {
            this.attributeValues = attributeValues != null ? new ArrayList<>(attributeValues) : new ArrayList<>();
        }

        public String getNamespaceSlug() {
            return namespaceSlug;
        }

        public void setNamespaceSlug(String namespaceSlug) {
            this.namespaceSlug = namespaceSlug;
        }

        public NamespaceRole getRole() {
            return role;
        }

        public void setRole(NamespaceRole role) {
            this.role = role;
        }
    }
}