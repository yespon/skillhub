package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Claims extractor for Ruijie SID OAuth2 provider.
 */
@Component
public class RuijieClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() { return "sourceid"; }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        String userId = String.valueOf(attrs.get("id"));

        Map<String, Object> attributes = (Map<String, Object>) attrs.get("attributes");
        String displayName = attributes != null ? (String) attributes.get("XM") : null;

        return new OAuthClaims(
            "sourceid",
            userId,
            null,  // 锐捷 SSO 不返回邮箱
            false,
            displayName != null ? displayName : userId,
            attrs
        );
    }
}
