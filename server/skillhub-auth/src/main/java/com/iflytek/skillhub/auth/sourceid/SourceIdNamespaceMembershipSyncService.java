package com.iflytek.skillhub.auth.sourceid;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reconciles SourceID and OSDS organization attributes into namespace memberships at login time.
 *
 * <p>This is intentionally additive only: it never removes or downgrades existing memberships.
 */
@Service
public class SourceIdNamespaceMembershipSyncService {

    private static final Logger log = LoggerFactory.getLogger(SourceIdNamespaceMembershipSyncService.class);

    private final SourceIdNamespaceSyncProperties properties;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final SourceIdOrganizationClient organizationClient;

    public SourceIdNamespaceMembershipSyncService(SourceIdNamespaceSyncProperties properties,
                                                  NamespaceRepository namespaceRepository,
                                                  NamespaceMemberRepository namespaceMemberRepository,
                                                  SourceIdOrganizationClient organizationClient) {
        this.properties = properties;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.organizationClient = organizationClient;
    }

    @Transactional
    public void reconcile(OAuthClaims claims, PlatformPrincipal principal) {
        if (!properties.isEnabled()
                || principal == null
                || claims == null
                || !matchesProvider(claims.provider(), properties.getProviderCode())
                || claims.extra() == null
                || properties.getMappings().isEmpty()) {
            return;
        }

        Map<String, Object> rootAttributes = new LinkedHashMap<>(claims.extra());
        loadOrganizationAttributes(claims.subject()).ifPresent(rootAttributes::putAll);
        Map<String, Object> profileAttributes = nestedAttributes(rootAttributes, properties.getAttributeRootKey());
        if (!isEligibleStatus(rootAttributes, profileAttributes)) {
            return;
        }

        Map<String, NamespaceRole> matchedNamespaces = properties.getMappings().stream()
                .filter(this::isValidMapping)
                .filter(mapping -> mappingMatches(mapping, rootAttributes, profileAttributes))
                .collect(Collectors.toMap(
                        SourceIdNamespaceSyncProperties.Mapping::getNamespaceSlug,
                        mapping -> normalizeRole(mapping.getRole()),
                        this::mergeRole,
                        LinkedHashMap::new
                ));

        for (Map.Entry<String, NamespaceRole> entry : matchedNamespaces.entrySet()) {
            syncNamespaceMembership(entry.getKey(), principal.userId(), entry.getValue());
        }
    }

    private boolean isValidMapping(SourceIdNamespaceSyncProperties.Mapping mapping) {
        return StringUtils.hasText(mapping.getAttributeKey())
                && !normalizedAttributeValues(mapping).isEmpty()
                && StringUtils.hasText(mapping.getNamespaceSlug());
    }

    private boolean mappingMatches(SourceIdNamespaceSyncProperties.Mapping mapping,
                                   Map<String, Object> rootAttributes,
                                   Map<String, Object> profileAttributes) {
        String actualValue = resolveAttribute(mapping.getAttributeKey(), rootAttributes, profileAttributes);
        if (actualValue == null) {
            return false;
        }
        String normalizedActualValue = actualValue.trim();
        return normalizedAttributeValues(mapping).stream()
                .anyMatch(candidate -> candidate.equals(normalizedActualValue));
    }

    private boolean isEligibleStatus(Map<String, Object> rootAttributes, Map<String, Object> profileAttributes) {
        if (!StringUtils.hasText(properties.getActiveStatusKey()) || properties.getActiveStatusValues().isEmpty()) {
            return true;
        }

        String actualStatus = resolveAttribute(properties.getActiveStatusKey(), rootAttributes, profileAttributes);
        if (!StringUtils.hasText(actualStatus)) {
            return true;
        }
        return properties.getActiveStatusValues().stream()
                .filter(StringUtils::hasText)
                .anyMatch(candidate -> candidate.trim().equals(actualStatus.trim()));
    }

    private void syncNamespaceMembership(String namespaceSlug, String userId, NamespaceRole role) {
        Optional<Namespace> namespaceOptional = namespaceRepository.findBySlug(namespaceSlug);
        if (namespaceOptional.isEmpty()) {
            log.warn("Skip SourceID namespace sync because namespace {} does not exist", namespaceSlug);
            return;
        }

        Namespace namespace = namespaceOptional.get();
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            log.info("Skip SourceID namespace sync for namespace {} because it is not active", namespaceSlug);
            return;
        }

        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), userId).isPresent()) {
            return;
        }

        namespaceMemberRepository.save(new NamespaceMember(namespace.getId(), userId, role));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedAttributes(Map<String, Object> rootAttributes, String rootKey) {
        if (!StringUtils.hasText(rootKey)) {
            return Map.of();
        }
        Object nested = rootAttributes.get(rootKey);
        if (nested instanceof Map<?, ?> nestedMap) {
            return (Map<String, Object>) nestedMap;
        }
        return Map.of();
    }

    private String resolveAttribute(String key, Map<String, Object> rootAttributes, Map<String, Object> profileAttributes) {
        Object value = rootAttributes.get(key);
        if (value == null) {
            value = profileAttributes.get(key);
        }
        return value != null ? String.valueOf(value) : null;
    }

    private boolean matchesProvider(String actual, String expected) {
        return actual != null && expected != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private Optional<Map<String, Object>> loadOrganizationAttributes(String userId) {
        return organizationClient.loadAttributesByUserId(userId);
    }
    private List<String> normalizedAttributeValues(SourceIdNamespaceSyncProperties.Mapping mapping) {
        return mapping.getAttributeValues().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private NamespaceRole normalizeRole(NamespaceRole role) {
        return role == null || role == NamespaceRole.OWNER ? NamespaceRole.MEMBER : role;
    }

    private NamespaceRole mergeRole(NamespaceRole left, NamespaceRole right) {
        return Comparator.comparingInt(this::roleWeight).compare(left, right) >= 0 ? left : right;
    }

    private int roleWeight(NamespaceRole role) {
        return switch (Objects.requireNonNullElse(role, NamespaceRole.MEMBER)) {
            case MEMBER -> 1;
            case ADMIN -> 2;
            case OWNER -> 1;
        };
    }
}