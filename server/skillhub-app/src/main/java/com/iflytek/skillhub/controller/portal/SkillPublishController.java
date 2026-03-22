package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillLabelAppService;
import com.iflytek.skillhub.service.SkillTranslationTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Upload endpoints for skill packages.
 *
 * <p>The controller is responsible for archive extraction and request shaping,
 * while the domain service owns all publication validation and state changes.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillPublishController extends BaseApiController {

    private static final Logger log = LoggerFactory.getLogger(SkillPublishController.class);

    private final SkillPublishService skillPublishService;
    private final SkillLabelAppService skillLabelAppService;
    private final SkillTranslationTaskService skillTranslationTaskService;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillHubMetrics skillHubMetrics;

    public SkillPublishController(SkillPublishService skillPublishService,
                                  SkillLabelAppService skillLabelAppService,
                                  SkillTranslationTaskService skillTranslationTaskService,
                                  SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                  ApiResponseFactory responseFactory,
                                  SkillHubMetrics skillHubMetrics) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
        this.skillLabelAppService = skillLabelAppService;
        this.skillTranslationTaskService = skillTranslationTaskService;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillHubMetrics = skillHubMetrics;
    }

    /**
     * Publishes an uploaded package into the target namespace after archive
     * extraction and visibility parsing.
     */
    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestParam(name = "displayNameZhCn", required = false) String displayNameZhCn,
            @RequestParam(name = "label", required = false) List<String> labels,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        SkillPublishService.PublishResult publishResult = skillPublishService.publishFromEntries(
                namespace,
                entries,
                principal.userId(),
                skillVisibility,
                principal.platformRoles(),
                normalizeOptionalDisplayName(displayNameZhCn)
        );

        if (labels != null && !labels.isEmpty()) {
            for (String labelSlug : labels) {
                try {
                    skillLabelAppService.attachLabel(
                            namespace,
                            publishResult.slug(),
                            labelSlug.trim().toLowerCase(java.util.Locale.ROOT),
                            principal.userId(),
                            userNsRoles != null ? userNsRoles : Map.of(),
                            AuditRequestContext.from(httpRequest)
                    );
                } catch (Exception e) {
                    log.warn("Failed to attach label '{}' to skill {}/{} during publish: {}",
                            labelSlug, namespace, publishResult.slug(), e.getMessage());
                }
            }
        }

        skillTranslationTaskService.maybeEnqueueForSkill(publishResult.skillId());
        PublishResponse response = new PublishResponse(
                publishResult.skillId(),
                namespace,
                publishResult.slug(),
                publishResult.version().getVersion(),
                publishResult.version().getStatus().name(),
                publishResult.version().getFileCount(),
                publishResult.version().getTotalSize(),
                normalizeOptionalDisplayName(displayNameZhCn)
        );
        skillHubMetrics.incrementSkillPublish(namespace, publishResult.version().getStatus().name());

        return ok("response.success.published", response);
    }

    private String normalizeOptionalDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
