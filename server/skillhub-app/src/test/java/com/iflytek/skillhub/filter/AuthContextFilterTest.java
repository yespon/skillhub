package com.iflytek.skillhub.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthContextFilterTest {

    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(
            messageSource,
            Clock.fixed(Instant.parse("2026-04-08T00:00:00Z"), ZoneOffset.UTC)
    );
    private final AuthContextFilter filter = new AuthContextFilter(
            namespaceMemberRepository,
            userAccountRepository,
            apiResponseFactory,
            new ObjectMapper(),
            true,
            new RouteSecurityPolicyRegistry()
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledSessionUser_invalidatesSessionAndBlocksRequest() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "Alice",
                "alice@example.com",
                null,
                "local",
                Set.of("USER")
        );
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills/global/demo");
        request.getSession(true).setAttribute("platformPrincipal", principal);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("error.auth.local.accountDisabled"), any(), eq("error.auth.local.accountDisabled"), any()))
                .thenReturn("account disabled");

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
        verify(namespaceMemberRepository, never()).findByUserId("user-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeSessionUser_populatesRequestContextAndContinues() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-2",
                "Bob",
                "bob@example.com",
                null,
                "local",
                Set.of("USER")
        );
        UserAccount user = new UserAccount("user-2", "Bob", "bob@example.com", null);
        user.setStatus(UserStatus.ACTIVE);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills/global/demo");
        request.getSession(true).setAttribute("platformPrincipal", principal);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        when(userAccountRepository.findById("user-2")).thenReturn(Optional.of(user));
        when(namespaceMemberRepository.findByUserId("user-2"))
                .thenReturn(List.of(new NamespaceMember(7L, "user-2", NamespaceRole.ADMIN)));

        filter.doFilter(request, response, filterChain);

        assertEquals("user-2", request.getAttribute("userId"));
        Map<Long, NamespaceRole> userNsRoles = (Map<Long, NamespaceRole>) request.getAttribute("userNsRoles");
        assertNotNull(userNsRoles);
        assertEquals(NamespaceRole.ADMIN, userNsRoles.get(7L));
        verify(filterChain).doFilter(request, response);
    }
}
