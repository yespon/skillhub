package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPermissionCheckerTest {

    private final ReviewPermissionChecker checker = new ReviewPermissionChecker();

    // --- canReview tests ---

    @Test
    void cannotReviewOwnSubmission() {
        Long userId = 1L;
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(), Set.of()));
    }

    @Test
    void teamAdminCanReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, 2L);
        assertTrue(checker.canReview(task, 1L,
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.ADMIN), Set.of()));
    }

    @Test
    void teamOwnerCanReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, 2L);
        assertTrue(checker.canReview(task, 1L,
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.OWNER), Set.of()));
    }

    @Test
    void teamMemberCannotReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, 2L);
        assertFalse(checker.canReview(task, 1L,
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.MEMBER), Set.of()));
    }

    @Test
    void skillAdminCanReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, 2L);
        assertTrue(checker.canReview(task, 1L,
                NamespaceType.GLOBAL,
                Map.of(), Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCanReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, 2L);
        assertTrue(checker.canReview(task, 1L,
                NamespaceType.GLOBAL,
                Map.of(), Set.of("SUPER_ADMIN")));
    }

    @Test
    void skillAdminCannotReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, 2L);
        assertFalse(checker.canReview(task, 1L,
                NamespaceType.TEAM,
                Map.of(), Set.of("SKILL_ADMIN")));
    }

    @Test
    void nonAdminCannotReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, 2L);
        assertFalse(checker.canReview(task, 1L,
                NamespaceType.GLOBAL,
                Map.of(), Set.of()));
    }

    // --- canReviewPromotion tests ---

    @Test
    void skillAdminCanReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, 2L);
        assertTrue(checker.canReviewPromotion(req, 1L,
                Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCanReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, 2L);
        assertTrue(checker.canReviewPromotion(req, 1L,
                Set.of("SUPER_ADMIN")));
    }

    @Test
    void regularUserCannotReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, 2L);
        assertFalse(checker.canReviewPromotion(req, 1L,
                Set.of()));
    }

    @Test
    void cannotReviewOwnPromotion() {
        Long userId = 2L;
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, userId);
        assertFalse(checker.canReviewPromotion(req, userId,
                Set.of("SKILL_ADMIN")));
    }
}
