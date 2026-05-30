package com.nebula.common.core.context;

import com.nebula.common.core.constant.SecurityConstants;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Request-scoped user context stored in the current thread.
 *
 * @author nebula
 */
public final class UserContext {

    private static final String WILDCARD_PERMISSION = "*";

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username) {
        set(userId, username, Collections.emptyList(), Collections.emptyList());
    }

    public static void set(Long userId) {
        set(userId, null);
    }

    public static void set(Long userId,
                           String username,
                           Collection<String> roles,
                           Collection<String> permissions) {
        HOLDER.set(new UserInfo(userId, username, normalize(roles), normalize(permissions)));
    }

    public static Long getUserId() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    public static String getUsername() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    public static List<String> getRoles() {
        UserInfo info = HOLDER.get();
        return info == null ? Collections.emptyList() : info.roles();
    }

    public static List<String> getPermissions() {
        UserInfo info = HOLDER.get();
        return info == null ? Collections.emptyList() : info.permissions();
    }

    public static boolean hasRole(String role) {
        return hasGrant(getRoles(), role) || isSuperAdmin();
    }

    public static boolean hasPermission(String permission) {
        return hasGrant(getPermissions(), permission) || isSuperAdmin();
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static boolean hasGrant(List<String> grants, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return grants.contains(WILDCARD_PERMISSION) || grants.contains(expected);
    }

    private static boolean isSuperAdmin() {
        List<String> roles = getRoles();
        return roles.contains(WILDCARD_PERMISSION) || roles.contains(SecurityConstants.ROLE_SUPER_ADMIN);
    }

    private static List<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    public record UserInfo(Long userId, String username, List<String> roles, List<String> permissions) {
    }
}
