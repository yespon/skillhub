package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuijieClaimsExtractorTest {

    @Test
    void extract_mapsSourceIdClaimsAndFallsBackToUserIdWhenNameMissing() {
        RuijieClaimsExtractor extractor = new RuijieClaimsExtractor();
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attributes = Map.of(
                "id", "source-user-1",
                "attributes", Map.of("XM", "锐捷用户")
        );
        when(oauth2User.getAttributes()).thenReturn(attributes);

        OAuthClaims claims = extractor.extract(request, oauth2User);

        assertThat(claims.provider()).isEqualTo("sourceid");
        assertThat(claims.subject()).isEqualTo("source-user-1");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("锐捷用户");
        assertThat(claims.extra()).isEqualTo(attributes);
    }

    @Test
    void extract_fallsBackToUserIdWhenDisplayNameMissing() {
        RuijieClaimsExtractor extractor = new RuijieClaimsExtractor();
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attributes = Map.of("id", "source-user-2");
        when(oauth2User.getAttributes()).thenReturn(attributes);

        OAuthClaims claims = extractor.extract(request, oauth2User);

        assertThat(claims.providerLogin()).isEqualTo("source-user-2");
    }
}