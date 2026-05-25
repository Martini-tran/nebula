package com.nebula.common.security.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 填充当前请求的 {@link UserContext} 用户上下文过滤器
 * <p>
 * 该过滤器从 Sa-Token 会话中提取用户认证信息，并填充到线程本地的 UserContext 中，
 * 供下游处理链路使用。过滤器顺序设置为 HIGHEST_PRECEDENCE + 20，确保在过滤器链中尽早执行。
 * </p>
 *
 * @author nebula
 */
@Slf4j
public class UserContextFilter extends OncePerRequestFilter implements Ordered {

    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private static final String SESSION_KEY_ROLES = SaSession.ROLE_LIST;
    private static final String SESSION_KEY_PERMISSIONS = SaSession.PERMISSION_LIST;

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        try {
            Long userId = getLoginIdAsLong();
            if (userId != null) {
                AuthSnapshot snapshot = loadAuthSnapshot(request, userId);
                String userName = request.getHeader(SecurityConstants.HEADER_USER_NAME);
                UserContext.set(userId, userName, snapshot.roles(), snapshot.permissions());

                if (log.isDebugEnabled()) {
                    log.debug("用户上下文填充成功 - userId: {}, userName: {}, 角色数: {}, 权限数: {}, 请求: {} {}",
                            userId, userName, snapshot.roles().size(), snapshot.permissions().size(), method, requestUri);
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("未找到已认证用户, 请求: {} {}", method, requestUri);
                }
                UserContext.clear();
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("用户上下文过滤器处理异常, 请求: {} {}", method, requestUri, e);
            UserContext.clear();
            throw e;
        } finally {
            UserContext.clear();
            if (log.isTraceEnabled()) {
                log.trace("用户上下文已清理, 请求: {} {}", method, requestUri);
            }
        }
    }

    /**
     * 从请求头（内部转发）或 Sa-Token 会话中加载认证信息快照
     *
     * @param request HTTP请求
     * @param userId  已认证的用户ID
     * @return 包含角色和权限的认证快照
     */
    private AuthSnapshot loadAuthSnapshot(HttpServletRequest request, Long userId) {
        long startTime = System.currentTimeMillis();

        try {
            List<String> roles;
            List<String> permissions;

            // 判断是否为携带头部数据的内部转发请求
            if (isInternalForwardedRequest(request, userId)) {
                List<String> headerRoles = parseList(request.getHeader(SecurityConstants.HEADER_USER_ROLES));
                List<String> headerPermissions = parseList(request.getHeader(SecurityConstants.HEADER_USER_PERMS));

                roles = !headerRoles.isEmpty() ? headerRoles : loadRolesFromSession();
                permissions = !headerPermissions.isEmpty() ? headerPermissions : loadPermissionsFromSession();

                if (log.isDebugEnabled() && (!headerRoles.isEmpty() || !headerPermissions.isEmpty())) {
                    log.debug("使用请求头认证数据 - userId: {}, 角色来源头: {}, 权限来源头: {}",
                            userId, !headerRoles.isEmpty(), !headerPermissions.isEmpty());
                }
            } else {
                roles = loadRolesFromSession();
                permissions = loadPermissionsFromSession();
            }

            // 如果会话数据为空，降级到直接调用 Sa-Token API
            if (roles.isEmpty()) {
                roles = safeLoadRoles();
                if (log.isWarnEnabled() && !roles.isEmpty()) {
                    log.warn("会话角色列表为空，降级到直接API调用获取到 {} 个角色, userId: {}", roles.size(), userId);
                }
            }

            if (permissions.isEmpty()) {
                permissions = safeLoadPermissions();
                if (log.isWarnEnabled() && !permissions.isEmpty()) {
                    log.warn("会话权限列表为空，降级到直接API调用获取到 {} 个权限, userId: {}", permissions.size(), userId);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            if (duration > 100 && log.isWarnEnabled()) {
                log.warn("认证快照加载耗时过长 - userId: {}, 耗时: {}ms", userId, duration);
            }

            return new AuthSnapshot(roles, permissions);

        } catch (Exception e) {
            log.error("加载认证快照失败 - userId: {}", userId, e);
            return new AuthSnapshot(Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 检查是否为包含用户数据的内部转发请求
     *
     * @param request HTTP请求
     * @param userId  已认证的用户ID
     * @return 如果是内部转发请求且userId匹配则返回true
     */
    private boolean isInternalForwardedRequest(HttpServletRequest request, Long userId) {
        String headerUserId = request.getHeader(SecurityConstants.HEADER_USER_ID);
        boolean matches = headerUserId != null && headerUserId.equals(String.valueOf(userId));

        if (matches && log.isTraceEnabled()) {
            log.trace("检测到内部转发请求 - userId: {}", userId);
        }
        return matches;
    }

    /**
     * 获取当前登录ID（Long类型）
     *
     * @return 登录ID，未认证时返回null
     */
    protected Long getLoginIdAsLong() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            if (log.isTraceEnabled()) {
                log.trace("成功获取登录ID: {}", userId);
            }
            return userId;
        } catch (NotLoginException e) {
            if (log.isTraceEnabled()) {
                log.trace("未找到登录会话 - 类型: {}, 消息: {}", e.getType(), e.getMessage());
            }
            return null;
        } catch (SaTokenException e) {
            if (log.isDebugEnabled()) {
                log.debug("SaToken异常 - 获取登录ID失败: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 从当前会话中加载指定键对应的列表
     *
     * @param key 会话键名
     * @return 字符串列表，未找到或异常时返回空列表
     */
    protected List<String> loadSessionList(String key) {
        try {
            Object value = StpUtil.getSession().get(key);
            List<String> result = toStringList(value);

            if (log.isTraceEnabled()) {
                log.trace("从会话加载列表 - key: {}, 数据量: {}", key, result.size());
            }
            return result;

        } catch (NotLoginException e) {
            log.trace("无法获取会话 - key: {}, 用户未登录", key);
            return Collections.emptyList();
        } catch (SaTokenException e) {
            log.debug("SaToken异常 - 加载会话列表失败, key: {}, 消息: {}", key, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("加载会话列表时发生未知异常 - key: {}", key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从会话加载角色列表
     */
    private List<String> loadRolesFromSession() {
        return loadSessionList(SESSION_KEY_ROLES);
    }

    /**
     * 从会话加载权限列表
     */
    private List<String> loadPermissionsFromSession() {
        return loadSessionList(SESSION_KEY_PERMISSIONS);
    }

    /**
     * 安全地直接从 Sa-Token API 加载角色列表
     *
     * @return 角色列表，异常时返回空列表
     */
    protected List<String> safeLoadRoles() {
        try {
            List<String> roles = StpUtil.getRoleList();
            if (log.isDebugEnabled()) {
                log.debug("直接API调用获取到 {} 个角色", roles.size());
            }
            return roles;
        } catch (NotLoginException e) {
            log.trace("无法加载角色 - 用户未登录");
            return Collections.emptyList();
        } catch (SaTokenException e) {
            log.debug("SaToken异常 - 加载角色失败: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("加载角色时发生未知异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 安全地直接从 Sa-Token API 加载权限列表
     *
     * @return 权限列表，异常时返回空列表
     */
    protected List<String> safeLoadPermissions() {
        try {
            List<String> permissions = StpUtil.getPermissionList();
            if (log.isDebugEnabled()) {
                log.debug("直接API调用获取到 {} 个权限", permissions.size());
            }
            return permissions;
        } catch (NotLoginException e) {
            log.trace("无法加载权限 - 用户未登录");
            return Collections.emptyList();
        } catch (SaTokenException e) {
            log.debug("SaToken异常 - 加载权限失败: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("加载权限时发生未知异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析逗号分隔的字符串为字符串列表
     *
     * @param value 逗号分隔的字符串
     * @return 解析后的字符串列表（已去重、去空白），输入为空时返回空列表
     */
    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        List<String> result = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (log.isTraceEnabled() && !result.isEmpty()) {
            log.trace("解析请求头字符串得到 {} 个数据项", result.size());
        }
        return result;
    }

    /**
     * 将不同集合/数组类型转换为字符串列表
     *
     * @param value 待转换的对象（Collection、数组或其他类型）
     * @return 转换后的字符串列表
     */
    private List<String> toStringList(Object value) {
        if (value == null) {
            if (log.isTraceEnabled()) {
                log.trace("toStringList 接收到null值");
            }
            return Collections.emptyList();
        }

        Stream<?> stream;

        if (value instanceof Collection<?> collection) {
            stream = collection.stream();
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            stream = IntStream.range(0, length).mapToObj(i -> Array.get(value, i));
        } else {
            // 降级处理：视为单个字符串并解析
            if (log.isDebugEnabled()) {
                log.debug("非集合类型 '{}' 转换为字符串列表 - 使用parseList降级处理", value.getClass().getSimpleName());
            }
            return parseList(value.toString());
        }

        List<String> result = stream
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (log.isTraceEnabled()) {
            log.trace("类型转换完成 - 输入大小: {}, 输出大小: {}", stream.count(), result.size());
        }
        return result;
    }

    /**
     * 用户认证数据不可变快照
     */
    private record AuthSnapshot(List<String> roles, List<String> permissions) {
        AuthSnapshot {
            // 防御性拷贝，确保不可变性
            roles = List.copyOf(roles != null ? roles : Collections.emptyList());
            permissions = List.copyOf(permissions != null ? permissions : Collections.emptyList());
        }
    }
}