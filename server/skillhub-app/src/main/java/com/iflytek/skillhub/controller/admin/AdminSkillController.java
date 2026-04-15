package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.AdminSkillMutationResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative skill-governance endpoints reserved for platform-level
 * moderation actions such as hide and unhide.
 */
@RestController
@RequestMapping("/api/v1/admin/skills")
public class AdminSkillController extends BaseApiController {

    private final SkillGovernanceService skillGovernanceService;
    private final SkillQueryService skillQueryService;

    public AdminSkillController(ApiResponseFactory responseFactory,
                                SkillGovernanceService skillGovernanceService,
                                SkillQueryService skillQueryService) {
        super(responseFactory);
        this.skillGovernanceService = skillGovernanceService;
        this.skillQueryService = skillQueryService;
    }

    @PostMapping("/{skillId}/hide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> hideSkill(@PathVariable Long skillId,
                                                             @RequestBody(required = false) AdminSkillActionRequest request,
                                                             @AuthenticationPrincipal PlatformPrincipal principal,
                                                             HttpServletRequest httpRequest) {
        var skill = skillGovernanceService.hideSkill(
            skillId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            request != null ? request.reason() : null
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(skillId, null, "HIDE", skill.getStatus().name()));
    }

    @PostMapping("/{skillId}/unhide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> unhideSkill(@PathVariable Long skillId,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        var skill = skillGovernanceService.unhideSkill(
            skillId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(skillId, null, "UNHIDE", skill.getStatus().name()));
    }

    @PostMapping("/versions/{versionId}/yank")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> yankVersion(@PathVariable Long versionId,
                                                               @RequestBody(required = false) AdminSkillActionRequest request,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        var version = skillGovernanceService.yankVersion(
            versionId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            request != null ? request.reason() : null
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(version.getSkillId(), versionId, "YANK", version.getStatus().name()));
    }

    @GetMapping("/versions/{versionId}/file")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<InputStreamResource> getVersionFile(@PathVariable Long versionId,
                                                              @RequestParam("path") String path) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(skillQueryService.getFileContentByVersionId(versionId, path)));
    }
}
