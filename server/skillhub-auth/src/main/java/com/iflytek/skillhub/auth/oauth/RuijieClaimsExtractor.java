package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Claims extractor for the SourceID provider used by Ruijie SSO.
 */
@Component
public class RuijieClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() {
        return "sourceid";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        String userId = String.valueOf(attrs.get("id"));

        Map<String, Object> attributes = (Map<String, Object>) attrs.get("attributes");
        String displayName = attributes != null ? (String) attributes.get("XM") : null;
        String rawEmail = attributes != null ? (String) attributes.get("RJEMAIL") : null;
        String email = StringUtils.hasText(rawEmail) ? rawEmail.trim() : null;

        return new OAuthClaims(
                "sourceid",
                userId,
                email,
                email != null,
                displayName != null ? displayName : userId,
                attrs
        );
    }
}
