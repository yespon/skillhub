package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.bootstrap.PassiveSessionAuthenticator;
import com.iflytek.skillhub.auth.policy.AccessPolicyFactory;
import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.AuthSessionBootstrapProperties;
import com.iflytek.skillhub.config.DirectAuthProperties;
import com.iflytek.skillhub.config.LocalAuthProperties;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;

class AuthMethodCatalogTest {

    private static LocalAuthProperties localAuthProperties(boolean showEntry) {
        LocalAuthProperties properties = new LocalAuthProperties();
        properties.setShowEntry(showEntry);
        return properties;
    }

    @Test
    void listMethodsShouldIncludeOnlyConfiguredOAuthProviders() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();

        OAuth2ClientProperties.Registration github = new OAuth2ClientProperties.Registration();
        github.setClientId("");
        github.setClientSecret("");
        github.setClientName("GitHub");
        oauthProperties.getRegistration().put("github", github);

        OAuth2ClientProperties.Registration sourceId = new OAuth2ClientProperties.Registration();
        sourceId.setClientId("sourceid-client");
        sourceId.setClientSecret("sourceid-secret");
        sourceId.setClientName("锐捷SSO");
        oauthProperties.getRegistration().put("sourceid", sourceId);

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            new DirectAuthProperties(),
            localAuthProperties(true),
            new AuthSessionBootstrapProperties(),
            List.of(),
            List.of(),
            new AccessPolicyFactory()
        );

        assertThat(catalog.listMethods("/dashboard"))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains("oauth-sourceid:锐捷SSO")
            .doesNotContain("oauth-github:GitHub");
    }

    @Test
    void listMethodsShouldHideProvidersOutsideAllowlist() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();

        OAuth2ClientProperties.Registration github = new OAuth2ClientProperties.Registration();
        github.setClientId("github-client");
        github.setClientSecret("github-secret");
        github.setClientName("GitHub");
        oauthProperties.getRegistration().put("github", github);

        OAuth2ClientProperties.Registration sourceId = new OAuth2ClientProperties.Registration();
        sourceId.setClientId("sourceid-client");
        sourceId.setClientSecret("sourceid-secret");
        sourceId.setClientName("锐捷SSO");
        oauthProperties.getRegistration().put("sourceid", sourceId);

        AccessPolicyFactory accessPolicyFactory = new AccessPolicyFactory();
        accessPolicyFactory.setMode("PROVIDER_ALLOWLIST");
        accessPolicyFactory.setAllowedProviders(List.of("sourceid"));

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            new DirectAuthProperties(),
            localAuthProperties(true),
            new AuthSessionBootstrapProperties(),
            List.of(),
            List.of(),
            accessPolicyFactory
        );

        assertThat(catalog.listMethods("/dashboard"))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains("oauth-sourceid:锐捷SSO")
            .doesNotContain("oauth-github:GitHub");
    }

    @Test
    void listMethodsShouldHideLocalPasswordWhenLocalEntryDisabled() {
        AuthMethodCatalog catalog = new AuthMethodCatalog(
            new OAuth2ClientProperties(),
            new DirectAuthProperties(),
            localAuthProperties(false),
            new AuthSessionBootstrapProperties(),
            List.of(),
            List.of(),
            new AccessPolicyFactory()
        );

        assertThat(catalog.listMethods(null))
            .extracting(AuthMethodResponse::id)
            .doesNotContain("local-password");
    }

    @Test
    void listMethodsShouldUseProviderDisplayNamesForCompatibleAuthMethods() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise Password";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                throw new UnsupportedOperationException("not used in catalog test");
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise SSO";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            localAuthProperties(true),
            bootstrapProperties,
            List.of(directProvider),
            List.of(bootstrapProvider),
            new AccessPolicyFactory()
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "local-password:Local Account",
                "direct-private-sso:Enterprise Password",
                "bootstrap-private-sso:Enterprise SSO"
            );
    }

    @Test
    void listMethodsShouldFallBackToProviderCodeWhenDisplayNameIsNotOverridden() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                return mock(PlatformPrincipal.class);
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            localAuthProperties(true),
            bootstrapProperties,
            List.of(directProvider),
            List.of(bootstrapProvider),
            new AccessPolicyFactory()
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "direct-private-sso:private-sso",
                "bootstrap-private-sso:private-sso"
            );
    }
}
