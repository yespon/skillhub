package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillTranslationRequest;
import com.iflytek.skillhub.dto.SkillTranslationResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillTranslationAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({
        "/api/v1/skills/{namespace}/{slug}/translations",
        "/api/web/skills/{namespace}/{slug}/translations"
})
public class SkillTranslationController extends BaseApiController {

    private final SkillTranslationAppService skillTranslationAppService;

    public SkillTranslationController(SkillTranslationAppService skillTranslationAppService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillTranslationAppService = skillTranslationAppService;
    }

    @GetMapping
    public ApiResponse<List<SkillTranslationResponse>> listTranslations(@PathVariable String namespace,
                                                                        @PathVariable String slug,
                                                                        @RequestAttribute("userId") String userId,
                                                                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok(
                "response.success.read",
                skillTranslationAppService.listSkillTranslations(namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of())
        );
    }

    @PutMapping("/{locale}")
    public ApiResponse<SkillTranslationResponse> upsertTranslation(@PathVariable String namespace,
                                                                   @PathVariable String slug,
                                                                   @PathVariable String locale,
                                                                   @RequestBody SkillTranslationRequest request,
                                                                   @RequestAttribute("userId") String userId,
                                                                   @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                   HttpServletRequest httpRequest) {
        return ok(
                "response.success.updated",
                skillTranslationAppService.upsertSkillTranslation(
                        namespace,
                        slug,
                        locale,
                        request,
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)
                )
        );
    }

    @DeleteMapping("/{locale}")
    public ApiResponse<MessageResponse> deleteTranslation(@PathVariable String namespace,
                                                          @PathVariable String slug,
                                                          @PathVariable String locale,
                                                          @RequestAttribute("userId") String userId,
                                                          @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                          HttpServletRequest httpRequest) {
        return ok(
                "response.success.deleted",
                skillTranslationAppService.deleteSkillTranslation(
                        namespace,
                        slug,
                        locale,
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)
                )
        );
    }
}