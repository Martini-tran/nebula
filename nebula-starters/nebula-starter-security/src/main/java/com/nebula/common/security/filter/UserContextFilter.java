package com.nebula.common.security.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates {@link UserContext} for the current servlet request.
 *
 * @author nebula
 */
public class UserContextFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            try {
                Long userId = getLoginIdAsLong();
                AuthSnapshot snapshot = loadAuthSnapshot(request, userId);
                UserContext.set(userId, request.getHeader(SecurityConstants.HEADER_USER_NAME),
                        snapshot.roles(), snapshot.permissions());
            } catch (NotLoginException | SaTokenContextException ignored) {
                UserContext.clear();
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private AuthSnapshot loadAuthSnapshot(HttpServletRequest request, Long userId) {
        List<String> headerRoles = Collections.emptyList();
        List<String> headerPermissions = Collections.emptyList();
        if (matchesLoginId(request, userId)) {
            headerRoles = parseList(request.getHeader(SecurityConstants.HEADER_USER_ROLES));
            headerPermissions = parseList(request.getHeader(SecurityConstants.HEADER_USER_PERMS));
        }

        List<String> roles = headerRoles.isEmpty() ? loadSessionList(SaSession.ROLE_LIST) : headerRoles;
        List<String> permissions = headerPermissions.isEmpty()
                ? loadSessionList(SaSession.PERMISSION_LIST)
                : headerPermissions;

        if (roles.isEmpty()) {
            roles = safeLoadRoles();
        }
        if (permissions.isEmpty()) {
            permissions = safeLoadPermissions();
        }
        return new AuthSnapshot(roles, permissions);
    }

    private boolean matchesLoginId(HttpServletRequest request, Long userId) {
        String headerUserId = request.getHeader(SecurityConstants.HEADER_USER_ID);
        return headerUserId != null && headerUserId.equals(String.valueOf(userId));
    }

    protected Long getLoginIdAsLong() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (SaTokenContextException e) {
            return null;
        }
    }

    protected List<String> loadSessionList(String key) {
        try {
            return toStringList(StpUtil.getSession().get(key));
        } catch (SaTokenContextException ignored) {
            return Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    protected List<String> safeLoadRoles() {
        try {
            return StpUtil.getRoleList();
        } catch (SaTokenContextException ignored) {
            return Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    protected List<String> safeLoadPermissions() {
        try {
            return StpUtil.getPermissionList();
        } catch (SaTokenContextException ignored) {
            return Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            return java.util.stream.IntStream.range(0, length)
                    .mapToObj(i -> Array.get(value, i))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        return parseList(value.toString());
    }

    private record AuthSnapshot(List<String> roles, List<String> permissions) {
    }
}
