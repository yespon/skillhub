package com.iflytek.skillhub.auth.sourceid;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceIdNamespaceMembershipSyncServiceTest {

    private final SourceIdNamespaceSyncProperties properties = new SourceIdNamespaceSyncProperties();
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final SourceIdOrganizationClient organizationClient = mock(SourceIdOrganizationClient.class);

    private SourceIdNamespaceMembershipSyncService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setActiveStatusKey("active");
        properties.setActiveStatusValues(java.util.List.of("true"));
        SourceIdNamespaceSyncProperties.Mapping mapping = new SourceIdNamespaceSyncProperties.Mapping();
        mapping.setAttributeKey("DWM");
        mapping.setAttributeValue("306000");
        mapping.setNamespaceSlug("team-network");
        mapping.setRole(NamespaceRole.MEMBER);
        properties.setMappings(java.util.List.of(mapping));
        when(organizationClient.loadAttributesByUserId(any())).thenReturn(Optional.empty());

        service = new SourceIdNamespaceMembershipSyncService(properties, namespaceRepository, namespaceMemberRepository, organizationClient);
    }

    @Test
    void reconcile_addsMissingMembershipWhenMappingMatches() {
        Namespace namespace = namespace(7L, "team-network", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-network")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(7L, "usr_1")).thenReturn(Optional.empty());

        service.reconcile(claims(Map.of("DWM", "306000")), principal("usr_1"));

        ArgumentCaptor<NamespaceMember> captor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getNamespaceId()).isEqualTo(7L);
        assertThat(captor.getValue().getUserId()).isEqualTo("usr_1");
        assertThat(captor.getValue().getRole()).isEqualTo(NamespaceRole.MEMBER);
    }

    @Test
    void reconcile_skipsWhenUserIsNotActive() {
        service.reconcile(claims(Map.of("DWM", "306000"), false), principal("usr_1"));

        verify(namespaceRepository, never()).findBySlug(any());
        verify(namespaceMemberRepository, never()).save(any());
    }

    @Test
    void reconcile_doesNotDuplicateExistingMembership() {
        Namespace namespace = namespace(7L, "team-network", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-network")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(7L, "usr_1"))
                .thenReturn(Optional.of(new NamespaceMember(7L, "usr_1", NamespaceRole.ADMIN)));

        service.reconcile(claims(Map.of("DWM", "306000")), principal("usr_1"));

        verify(namespaceMemberRepository, never()).save(any());
    }

    @Test
    void reconcile_addsUserToMultipleNamespacesWhenSameSourceTeamMatchesMultipleMappings() {
        SourceIdNamespaceSyncProperties.Mapping adminMapping = new SourceIdNamespaceSyncProperties.Mapping();
        adminMapping.setAttributeKey("DWM");
        adminMapping.setAttributeValue("306000");
        adminMapping.setNamespaceSlug("team-admin");
        adminMapping.setRole(NamespaceRole.ADMIN);
        properties.setMappings(java.util.List.of(properties.getMappings().getFirst(), adminMapping));

        Namespace networkNamespace = namespace(7L, "team-network", NamespaceStatus.ACTIVE);
        Namespace adminNamespace = namespace(9L, "team-admin", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-network")).thenReturn(Optional.of(networkNamespace));
        when(namespaceRepository.findBySlug("team-admin")).thenReturn(Optional.of(adminNamespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(7L, "usr_1")).thenReturn(Optional.empty());
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(9L, "usr_1")).thenReturn(Optional.empty());

        service.reconcile(claims(Map.of("DWM", "306000")), principal("usr_1"));

        ArgumentCaptor<NamespaceMember> captor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NamespaceMember::getNamespaceId, NamespaceMember::getRole)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(7L, NamespaceRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple(9L, NamespaceRole.ADMIN)
                );
    }

    @Test
    void reconcile_matchesAnyConfiguredAttributeValueForSameNamespace() {
        SourceIdNamespaceSyncProperties.Mapping mapping = new SourceIdNamespaceSyncProperties.Mapping();
        mapping.setAttributeKey("DWM");
        mapping.setAttributeValues(java.util.List.of("306000", "306001", "306002"));
        mapping.setNamespaceSlug("team-network");
        mapping.setRole(NamespaceRole.MEMBER);
        properties.setMappings(java.util.List.of(mapping));

        Namespace namespace = namespace(7L, "team-network", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-network")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(7L, "usr_1")).thenReturn(Optional.empty());

        service.reconcile(claims(Map.of("DWM", "306001")), principal("usr_1"));

        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void reconcile_matchesOsdsDepartmentAttributesWhenProfileLacksTeamField() {
        SourceIdNamespaceSyncProperties.Mapping mapping = new SourceIdNamespaceSyncProperties.Mapping();
        mapping.setAttributeKey("departmentCode");
        mapping.setAttributeValue("000023002");
        mapping.setNamespaceSlug("team-ops");
        properties.setMappings(java.util.List.of(mapping));

        Namespace namespace = namespace(11L, "team-ops", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-ops")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(11L, "usr_1")).thenReturn(Optional.empty());
        when(organizationClient.loadAttributesByUserId("20042020"))
                .thenReturn(Optional.of(Map.of("departmentCode", "000023002", "isEnable", true, "staffStatus", 1)));

        service.reconcile(claims(Map.of("XM", "张三")), principal("usr_1"));

        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void reconcile_skipsNamespaceSyncWhenOsdsLookupFailsOpen() {
        when(organizationClient.loadAttributesByUserId("20042020")).thenReturn(Optional.empty());

        service.reconcile(claims(Map.of("XM", "张三")), principal("usr_1"));

        verify(namespaceRepository, never()).findBySlug(any());
        verify(namespaceMemberRepository, never()).save(any());
    }

    @Test
    void reconcile_propagatesOsdsLookupFailureWhenClientFailsClosed() {
        when(organizationClient.loadAttributesByUserId("20042020")).thenThrow(new IllegalStateException("osds unavailable"));

        assertThatThrownBy(() -> service.reconcile(claims(Map.of("XM", "张三")), principal("usr_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("osds unavailable");
    }

    // ── OSDS dialect evidence tests (staffStatus / departmentCode config) ─────────────

    /**
     * 证据A: OSDS returns staffStatus=1 (在职) + departmentCode=000023 → user auto-added to it-team as MEMBER.
     */
    @Test
    void evidenceA_osdsDialect_addsMemberWhenDepartmentCodeMatches() {
        configureOsdsDialect();
        when(organizationClient.loadAttributesByUserId("osds-user-001"))
                .thenReturn(Optional.of(Map.of("departmentCode", "000023", "staffStatus", 1)));
        Namespace namespace = namespace(2L, "it-team", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("it-team")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(2L, "u_osds_1")).thenReturn(Optional.empty());

        service.reconcile(osdsDialectClaims("osds-user-001"), principal("u_osds_1"));

        ArgumentCaptor<NamespaceMember> captor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getNamespaceId()).isEqualTo(2L);
        assertThat(captor.getValue().getUserId()).isEqualTo("u_osds_1");
        assertThat(captor.getValue().getRole()).isEqualTo(NamespaceRole.MEMBER);
    }

    /**
     * 证据B: OSDS returns a departmentCode that does not match any mapping → no membership change.
     */
    @Test
    void evidenceB_osdsDialect_skipsWhenDepartmentCodeDoesNotMatch() {
        configureOsdsDialect();
        when(organizationClient.loadAttributesByUserId("osds-user-002"))
                .thenReturn(Optional.of(Map.of("departmentCode", "999999", "staffStatus", 1)));

        service.reconcile(osdsDialectClaims("osds-user-002"), principal("u_osds_2"));

        verify(namespaceRepository, never()).findBySlug(any());
        verify(namespaceMemberRepository, never()).save(any());
    }

    /**
     * 证据C: User already holds ADMIN role in it-team; sync must not downgrade to MEMBER.
     */
    @Test
    void evidenceC_osdsDialect_doesNotDowngradeExistingAdminRole() {
        configureOsdsDialect();
        when(organizationClient.loadAttributesByUserId("osds-user-003"))
                .thenReturn(Optional.of(Map.of("departmentCode", "000023", "staffStatus", 1)));
        Namespace namespace = namespace(2L, "it-team", NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("it-team")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(2L, "u_osds_3"))
                .thenReturn(Optional.of(new NamespaceMember(2L, "u_osds_3", NamespaceRole.ADMIN)));

        service.reconcile(osdsDialectClaims("osds-user-003"), principal("u_osds_3"));

        verify(namespaceMemberRepository, never()).save(any());
    }

    /**
     * 证据D: OSDS lookup fails open → login must not be blocked, no membership change.
     */
    @Test
    void evidenceD_osdsDialect_doesNotBlockLoginWhenOsdsUnavailable() {
        configureOsdsDialect();
        when(organizationClient.loadAttributesByUserId("osds-user-004"))
                .thenReturn(Optional.empty());

        // Must not throw; login flow continues
        service.reconcile(osdsDialectClaims("osds-user-004"), principal("u_osds_4"));

        verify(namespaceMemberRepository, never()).save(any());
    }

    @Test
    void evidenceE_osdsDialect_skipsWhenConfiguredStatusFieldIsMissing() {
        configureOsdsDialect();
        when(organizationClient.loadAttributesByUserId("osds-user-005"))
                .thenReturn(Optional.of(Map.of("departmentCode", "000023")));

        service.reconcile(osdsDialectClaims("osds-user-005"), principal("u_osds_5"));

        verify(namespaceRepository, never()).findBySlug(any());
        verify(namespaceMemberRepository, never()).save(any());
    }

    // ── OSDS dialect helpers ───────────────────────────────────────────────────────

    private void configureOsdsDialect() {
        properties.setActiveStatusKey("staffStatus");
        properties.setActiveStatusValues(java.util.List.of("0", "1"));
        SourceIdNamespaceSyncProperties.Mapping mapping = new SourceIdNamespaceSyncProperties.Mapping();
        mapping.setAttributeKey("departmentCode");
        mapping.setAttributeValue("000023");
        mapping.setNamespaceSlug("it-team");
        mapping.setRole(NamespaceRole.MEMBER);
        properties.setMappings(java.util.List.of(mapping));
    }

    private OAuthClaims osdsDialectClaims(String subject) {
        // SourceID OAuth claims without departmentCode/isEnable — those come from OSDS
        return new OAuthClaims(
                "sourceid",
                subject,
                null,
                false,
                "OSDS测试用户",
                Map.of("id", subject)
        );
    }

    @Test
    void reconcile_propagatesOsdsLookupFailureWhenClientFailsClosed() {
        when(organizationClient.loadAttributesByUserId("20042020")).thenThrow(new IllegalStateException("osds unavailable"));

        assertThatThrownBy(() -> service.reconcile(claims(Map.of("XM", "张三")), principal("usr_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("osds unavailable");
    }

    private OAuthClaims claims(Map<String, Object> sourceIdAttributes) {
        return claims(sourceIdAttributes, true);
    }

    private OAuthClaims claims(Map<String, Object> sourceIdAttributes, boolean active) {
        return new OAuthClaims(
                "sourceid",
                "20042020",
                null,
                false,
                "张三",
                Map.of("id", "20042020", "active", active, "attributes", sourceIdAttributes)
        );
    }

    private PlatformPrincipal principal(String userId) {
        return new PlatformPrincipal(userId, "张三", null, null, "sourceid", Set.of("USER"));
    }

    private Namespace namespace(Long id, String slug, NamespaceStatus status) {
        Namespace namespace = new Namespace(slug, slug, "owner-1");
        try {
            var idField = Namespace.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(namespace, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        namespace.setStatus(status);
        return namespace;
    }
}