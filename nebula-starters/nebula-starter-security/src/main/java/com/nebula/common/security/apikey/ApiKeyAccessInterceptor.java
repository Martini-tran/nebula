package com.nebula.common.security.apikey;

import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import cn.dev33.satoken.apikey.error.SaApiKeyErrorCode;
import cn.dev33.satoken.apikey.exception.ApiKeyScopeException;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import com.nebula.common.core.context.UserContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 访问守卫：凭 API Key 进来的请求，只能访问显式标注了 {@link SaCheckApiKey} 的接口
 *
 * <p>Sa-Token 的 {@code @SaCheckApiKey} 只负责「放行标注了的接口」，不负责「拦住没标注的接口」。
 * 而网关对带合法 key 的请求不再做登录校验，各服务又有不少接口只靠网关的登录校验兜底（没有任何鉴权注解），
 * 不补这一层，任意一把合法 key 都能直接打到这些接口上。本拦截器就是这条默认拒绝规则。</p>
 *
 * <p>校验通过后把 key 的所属用户写入 {@link UserContext}，业务代码取当前用户的方式与登录态一致。
 * 这里刻意不用 {@code StpUtil.switchTo}：它会让 key 通过该用户的所有 {@code @SaCheckPermission}，
 * 白名单随之失效。</p>
 *
 * <p>已登录的请求不受影响（即便顺带带了 key），仍按登录态的权限走。</p>
 *
 * @author nebula
 */
public class ApiKeyAccessInterceptor implements HandlerInterceptor {

    private static final String ATTR_CONTEXT_SET = ApiKeyAccessInterceptor.class.getName() + ".contextSet";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 异步回归等非初始 dispatch：初始请求已校验过，且此时 Sa-Token 上下文可能已被清理
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        String apiKey = readApiKey();
        if (apiKey == null || isLogin()) {
            return true;
        }

        // 先验 key 本身（无效/过期/禁用），再看接口是否开放、scope 是否足够
        ApiKeyModel model = checkApiKey(apiKey);
        List<SaCheckApiKey> requirements = findRequirements(handler);
        if (requirements.isEmpty()) {
            throw new ApiKeyScopeException("该接口未开放 API Key 访问")
                    .setApiKey(apiKey)
                    .setCode(SaApiKeyErrorCode.CODE_12311);
        }
        for (SaCheckApiKey requirement : requirements) {
            checkScope(apiKey, requirement.scope(), requirement.mode());
        }

        UserContext.set(toUserId(model.getLoginId()));
        request.setAttribute(ATTR_CONTEXT_SET, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (request.getAttribute(ATTR_CONTEXT_SET) != null) {
            UserContext.clear();
        }
    }

    /**
     * 收集接口上的 {@code @SaCheckApiKey}：类上与方法上的都要满足，与 Sa-Token 自身的注解校验顺序一致。
     */
    private List<SaCheckApiKey> findRequirements(Object handler) {
        List<SaCheckApiKey> found = new ArrayList<>(2);
        if (handler instanceof HandlerMethod handlerMethod) {
            addIfPresent(found, handlerMethod.getMethod().getDeclaringClass());
            addIfPresent(found, handlerMethod.getMethod());
        }
        return found;
    }

    private void addIfPresent(List<SaCheckApiKey> found, AnnotatedElement element) {
        SaCheckApiKey annotation =
                (SaCheckApiKey) SaAnnotationStrategy.instance.getAnnotation.apply(element, SaCheckApiKey.class);
        if (annotation != null) {
            found.add(annotation);
        }
    }

    private Long toUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(loginId));
    }

    /**
     * 从当前请求读取 API Key（参数 {@code apikey} → 请求头 {@code apikey} → Basic 认证），读不到返回 null
     */
    protected String readApiKey() {
        return SaApiKeyUtil.readApiKeyValue(SaHolder.getRequest());
    }

    /**
     * 当前请求是否带着有效的登录态
     */
    protected boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 校验 key 本身，失败抛 {@link cn.dev33.satoken.apikey.exception.ApiKeyException}
     */
    protected ApiKeyModel checkApiKey(String apiKey) {
        return SaApiKeyUtil.checkApiKey(apiKey);
    }

    /**
     * 校验 key 是否具备指定 scope，失败抛 {@link ApiKeyScopeException}
     */
    protected void checkScope(String apiKey, String[] scopes, SaMode mode) {
        if (mode == SaMode.AND) {
            SaApiKeyUtil.checkApiKeyScope(apiKey, scopes);
        } else {
            SaApiKeyUtil.checkApiKeyScopeOr(apiKey, scopes);
        }
    }
}
