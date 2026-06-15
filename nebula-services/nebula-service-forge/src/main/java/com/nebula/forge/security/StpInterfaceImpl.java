package com.nebula.forge.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Forge service reads Sa-Token auth data from the shared Redis-backed session.
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return getSessionList(loginId, SaSession.PERMISSION_LIST);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return getSessionList(loginId, SaSession.ROLE_LIST);
    }

    private List<String> getSessionList(Object loginId, String key) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        Object value = StpUtil.getSessionByLoginId(loginId).get(key);
        return toStringList(value);
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
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
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        return Arrays.stream(value.toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }
}
