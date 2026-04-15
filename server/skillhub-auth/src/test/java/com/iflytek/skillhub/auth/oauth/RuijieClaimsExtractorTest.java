package com.iflytek.skillhub.auth.oauth;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import static org.assertj.core.api.Assertions.assertThat;

class RuijieClaimsExtractorTest {

    private final RuijieClaimsExtractor extractor = new RuijieClaimsExtractor();

    @Test
    void extract_readsEmailFromRjEmailAttribute() {
        OAuthClaims claims = extractor.extract(
                null,
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", "20042020",
                                "attributes", Map.of(
                                        "XM", "张三",
                                        "RJEMAIL", "zhangsan@example.com"
                                )
                        ),
                        "id"
                )
        );

        assertThat(claims.subject()).isEqualTo("20042020");
        assertThat(claims.providerLogin()).isEqualTo("张三");
        assertThat(claims.email()).isEqualTo("zhangsan@example.com");
        assertThat(claims.emailVerified()).isTrue();
    }

    @Test
    void extract_treatsBlankRjEmailAsMissing() {
        OAuthClaims claims = extractor.extract(
                null,
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", "20042020",
                                "attributes", Map.of(
                                        "XM", "张三",
                                        "RJEMAIL", "   "
                                )
                        ),
                        "id"
                )
        );

        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
    }
}