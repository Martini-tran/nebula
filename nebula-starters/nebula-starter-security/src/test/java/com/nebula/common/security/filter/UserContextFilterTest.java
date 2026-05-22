package com.nebula.common.security.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.context.UserContext;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextFilterTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void usesMatchingGatewayHeadersAndClearsAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_USER_ID, "12");
        request.addHeader(SecurityConstants.HEADER_USER_NAME, "alice");
        request.addHeader(SecurityConstants.HEADER_USER_ROLES, " admin, editor,admin ");
        request.addHeader(SecurityConstants.HEADER_USER_PERMS, " blog:add,blog:edit, blog:add ");
        CapturingFilterChain chain = new CapturingFilterChain();

        new StubUserContextFilter(12L, List.of("session-role"), List.of("session:permission"),
                List.of("fallback-role"), List.of("fallback:permission"))
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(List.of("admin", "editor"), chain.roles);
        assertEquals(List.of("blog:add", "blog:edit"), chain.permissions);
        assertTrue(chain.hasAdminRole);
        assertTrue(chain.hasAddPermission);
        assertNull(UserContext.get());
    }

    @Test
    void ignoresMismatchedGatewayHeadersAndFallsBackToSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_USER_ID, "99");
        request.addHeader(SecurityConstants.HEADER_USER_ROLES, " forged ");
        request.addHeader(SecurityConstants.HEADER_USER_PERMS, " forged:permission ");
        CapturingFilterChain chain = new CapturingFilterChain();

        new StubUserContextFilter(12L, List.of("session-role"), List.of("session:permission"),
                List.of("fallback-role"), List.of("fallback:permission"))
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(List.of("session-role"), chain.roles);
        assertEquals(List.of("session:permission"), chain.permissions);
        assertFalse(chain.hasAdminRole);
        assertFalse(chain.hasAddPermission);
        assertNull(UserContext.get());
    }

    @Test
    void fallsBackToStpListsWhenSessionIsEmpty() throws Exception {
        CapturingFilterChain chain = new CapturingFilterChain();

        new StubUserContextFilter(12L, Collections.emptyList(), Collections.emptyList(),
                List.of("fallback-role"), List.of("fallback:permission"))
                .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals(List.of("fallback-role"), chain.roles);
        assertEquals(List.of("fallback:permission"), chain.permissions);
        assertNull(UserContext.get());
    }

    @Test
    void unauthenticatedRequestLeavesContextEmpty() throws Exception {
        CapturingFilterChain chain = new CapturingFilterChain();

        new NotLoginUserContextFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(chain.userId);
        assertEquals(List.of(), chain.roles);
        assertEquals(List.of(), chain.permissions);
        assertNull(UserContext.get());
    }

    private static class CapturingFilterChain extends MockFilterChain {

        Long userId;
        List<String> roles;
        List<String> permissions;
        boolean hasAdminRole;
        boolean hasAddPermission;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) throws IOException, ServletException {
            userId = UserContext.getUserId();
            roles = UserContext.getRoles();
            permissions = UserContext.getPermissions();
            hasAdminRole = UserContext.hasRole("admin");
            hasAddPermission = UserContext.hasPermission("blog:add");
        }
    }

    private static class StubUserContextFilter extends UserContextFilter {

        private final Long loginId;
        private final List<String> sessionRoles;
        private final List<String> sessionPermissions;
        private final List<String> fallbackRoles;
        private final List<String> fallbackPermissions;

        private StubUserContextFilter(Long loginId,
                                      List<String> sessionRoles,
                                      List<String> sessionPermissions,
                                      List<String> fallbackRoles,
                                      List<String> fallbackPermissions) {
            this.loginId = loginId;
            this.sessionRoles = sessionRoles;
            this.sessionPermissions = sessionPermissions;
            this.fallbackRoles = fallbackRoles;
            this.fallbackPermissions = fallbackPermissions;
        }

        @Override
        protected Long getLoginIdAsLong() {
            return loginId;
        }

        @Override
        protected List<String> loadSessionList(String key) {
            if (SaSession.ROLE_LIST.equals(key)) {
                return sessionRoles;
            }
            if (SaSession.PERMISSION_LIST.equals(key)) {
                return sessionPermissions;
            }
            return Collections.emptyList();
        }

        @Override
        protected List<String> safeLoadRoles() {
            return fallbackRoles;
        }

        @Override
        protected List<String> safeLoadPermissions() {
            return fallbackPermissions;
        }
    }

    private static class NotLoginUserContextFilter extends UserContextFilter {

        @Override
        protected Long getLoginIdAsLong() {
            throw new NotLoginException("not login", StpUtilType.TYPE, NotLoginException.NOT_TOKEN);
        }
    }

    private static final class StpUtilType {

        private static final String TYPE = "login";

        private StpUtilType() {
        }
    }
}
