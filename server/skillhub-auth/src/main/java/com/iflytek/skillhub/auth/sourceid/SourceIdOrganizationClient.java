package com.iflytek.skillhub.auth.sourceid;

import java.util.Map;
import java.util.Optional;

/**
 * Loads organization attributes for SourceID users from external systems such as OSDS.
 */
public interface SourceIdOrganizationClient {

    Optional<Map<String, Object>> loadAttributesByUserId(String userId);
}