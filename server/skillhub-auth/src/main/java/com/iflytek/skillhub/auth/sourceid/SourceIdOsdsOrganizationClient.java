package com.iflytek.skillhub.auth.sourceid;

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
                        if (StringUtils.hasText(properties.getSignServerAuth())) {
                            headers.add("sign-server-auth", properties.getSignServerAuth().trim());
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return extractData(response);
        } catch (RestClientResponseException e) {
            if (properties.isFailOpen()) {
                log.warn("Skip OSDS organization lookup for user {} because OSDS returned status {}", userId, e.getStatusCode().value());
                return Optional.empty();
            }
            throw e;
        } catch (RuntimeException e) {
            if (properties.isFailOpen()) {
                log.warn("Skip OSDS organization lookup for user {} because OSDS request failed: {}", userId, e.getMessage());
                return Optional.empty();
            }
            throw e;
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
}