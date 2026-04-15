package com.iflytek.skillhub.auth.sourceid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Loads SourceID user organization data from OSDS.
 */
@Component
public class SourceIdOsdsOrganizationClient implements SourceIdOrganizationClient {

    private static final Logger log = LoggerFactory.getLogger(SourceIdOsdsOrganizationClient.class);
    private static final String SIGN_DELIMITER = "|";

    private final SourceIdOsdsProperties properties;
    private final RestClient restClient;

    public SourceIdOsdsOrganizationClient(SourceIdOsdsProperties properties) {
        this.properties = properties;
        this.restClient = StringUtils.hasText(properties.getBaseUrl())
                ? RestClient.builder()
                        .baseUrl(properties.getBaseUrl().trim())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build()
                : null;
    }

    @Override
    public Optional<Map<String, Object>> loadAttributesByUserId(String userId) {
        if (!properties.isEnabled() || restClient == null || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.getStaffByUserIdPath()).build(userId))
                    .headers(headers -> {
                        if (StringUtils.hasText(properties.getSysid())) {
                            headers.add("sysid", properties.getSysid().trim());
                        }
                        resolveSignServerAuthHeaderValue().ifPresent(signature -> headers.add("sign-server-auth", signature));
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return extractData(response);
        } catch (RestClientResponseException e) {
            log.warn("Skip OSDS organization lookup for user {} because OSDS returned status {}", userId, e.getStatusCode().value());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Skip OSDS organization lookup for user {} because OSDS request failed: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> extractData(Map<String, Object> response) {
        if (response == null) {
            return Optional.empty();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return Optional.empty();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        dataMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return Optional.of(normalized);
    }

    private Optional<String> resolveSignServerAuthHeaderValue() {
        String appId = StringUtils.hasText(properties.getSysid()) ? properties.getSysid().trim() : null;
        String accessKeySecret = StringUtils.hasText(properties.getAccessKeySecret())
                ? properties.getAccessKeySecret().trim()
                : null;

        if (StringUtils.hasText(appId) && StringUtils.hasText(accessKeySecret)) {
            long timestamp = System.currentTimeMillis();
            String digest = md5Uppercase32(appId + timestamp + accessKeySecret);
            return Optional.of(appId + SIGN_DELIMITER + timestamp + SIGN_DELIMITER + digest);
        }

        if (StringUtils.hasText(properties.getSignServerAuth())) {
            log.warn("OSDS accessKeySecret is not configured, fallback to static sign-server-auth header");
            return Optional.of(properties.getSignServerAuth().trim());
        }
        return Optional.empty();
    }

    private String md5Uppercase32(String content) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available", e);
        }
    }
}