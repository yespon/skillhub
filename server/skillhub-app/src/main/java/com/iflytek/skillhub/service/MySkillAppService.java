package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MySkillAppService {
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillStarRepository skillStarRepository;
    private final PromotionRequestRepository promotionRequestRepository;

    public MySkillAppService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SkillStarRepository skillStarRepository,
            PromotionRequestRepository promotionRequestRepository) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillStarRepository = skillStarRepository;
        this.promotionRequestRepository = promotionRequestRepository;
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId, int page, int size) {
        Page<Skill> skillPage = skillRepository.findByOwnerId(userId, PageRequest.of(page, size));
        List<Skill> skills = skillPage.getContent();

        Map<Long, SkillVersion> versionsBySkillId = loadLatestRelevantVersions(skills);

        List<Long> namespaceIds = skills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(com.iflytek.skillhub.domain.namespace.Namespace::getId, Function.identity()));

        List<SkillSummaryResponse> items = skills.stream()
                .map(skill -> toSummaryResponse(skill, versionsBySkillId, namespacesById))
                .toList();

        return new PageResponse<>(items, skillPage.getTotalElements(), skillPage.getNumber(), skillPage.getSize());
    }

    public PageResponse<SkillSummaryResponse> listMyStars(String userId, int page, int size) {
        Page<com.iflytek.skillhub.domain.social.SkillStar> starPage = skillStarRepository.findByUserId(
                userId,
                PageRequest.of(page, size)
        );
        List<com.iflytek.skillhub.domain.social.SkillStar> stars = starPage.getContent();

        List<Long> skillIds = stars.stream()
                .map(com.iflytek.skillhub.domain.social.SkillStar::getSkillId)
                .distinct()
                .toList();
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));

        Map<Long, SkillVersion> versionsBySkillId = loadLatestRelevantVersions(skillsById.values());

        List<Long> namespaceIds = skillsById.values().stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(com.iflytek.skillhub.domain.namespace.Namespace::getId, Function.identity()));

        List<SkillSummaryResponse> items = stars.stream()
                .map(star -> skillsById.get(star.getSkillId()))
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(skill, versionsBySkillId, namespacesById))
                .toList();

        return new PageResponse<>(items, starPage.getTotalElements(), starPage.getNumber(), starPage.getSize());
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, SkillVersion> versionsBySkillId,
            Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById) {
        SkillVersion latestVersion = versionsBySkillId.get(skill.getId());
        com.iflytek.skillhub.domain.namespace.Namespace namespace = namespacesById.get(skill.getNamespaceId());

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                Optional.ofNullable(latestVersion).map(SkillVersion::getVersion).orElse(null),
                Optional.ofNullable(latestVersion).map(SkillVersion::getId).orElse(null),
                Optional.ofNullable(latestVersion).map(SkillVersion::getStatus).map(Enum::name).orElse(null),
                namespace != null ? namespace.getSlug() : null,
                skill.getUpdatedAt(),
                canSubmitPromotion(skill, latestVersion, namespace)
        );
    }

    private boolean canSubmitPromotion(
            Skill skill,
            SkillVersion latestVersion,
            com.iflytek.skillhub.domain.namespace.Namespace namespace) {
        if (namespace == null) {
            return false;
        }
        if (namespace.getType() == NamespaceType.GLOBAL) {
            return false;
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.PENDING).isPresent()) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.APPROVED).isPresent()) {
            return false;
        }
        return latestVersion != null && latestVersion.getStatus() == SkillVersionStatus.PUBLISHED;
    }

    private Map<Long, SkillVersion> loadLatestRelevantVersions(java.util.Collection<Skill> skills) {
        if (skills.isEmpty()) {
            return Map.of();
        }

        List<Long> explicitLatestVersionIds = skills.stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = explicitLatestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(explicitLatestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        List<Long> skillIdsNeedingFallback = skills.stream()
                .filter(skill -> skill.getLatestVersionId() == null || !versionsById.containsKey(skill.getLatestVersionId()))
                .map(Skill::getId)
                .distinct()
                .toList();
        Map<Long, SkillVersion> fallbackBySkillId = skillIdsNeedingFallback.isEmpty()
                ? Map.of()
                : skillVersionRepository.findBySkillIdIn(skillIdsNeedingFallback).stream()
                        .filter(version -> version.getStatus() != SkillVersionStatus.YANKED)
                        .collect(Collectors.toMap(
                                SkillVersion::getSkillId,
                                Function.identity(),
                                (left, right) -> left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right
                        ));

        Map<Long, SkillVersion> resolvedVersions = new java.util.HashMap<>();
        for (Skill skill : skills) {
            SkillVersion resolvedVersion = skill.getLatestVersionId() != null
                    ? versionsById.get(skill.getLatestVersionId())
                    : fallbackBySkillId.get(skill.getId());
            if (resolvedVersion != null) {
                resolvedVersions.put(skill.getId(), resolvedVersion);
            }
        }
        return resolvedVersions;
    }
}
