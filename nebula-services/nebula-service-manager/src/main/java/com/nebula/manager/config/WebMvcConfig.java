package com.nebula.manager.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置
 * 注册 Sa-Token 拦截器，使 {@code @SaCheckLogin} / {@code @SaCheckPermission} / {@code @SaCheckRole}
 * 等基于注解的鉴权能在控制器方法上生效。登录态校验主要由网关完成，这里只兜底权限/角色校验。
 *
 * @author nebula
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AsyncAwareSaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/captcha/get", "/captcha/check");
    }

    /**
     * 仅在初始请求（DispatcherType.REQUEST）做 Sa-Token 注解校验的拦截器。
     *
     * <p>返回 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter} 的端点是异步请求：
     * 初始请求校验通过后，SSE 在独立线程写出，容器随后发起 {@code ASYNC} dispatch 回归。回归时若拦截器
     * 再次执行 {@code @SaCheckPermission}，而承载 {@code SaTokenContext} 的原始请求上下文已随初始请求结束被清理，
     * 就会抛 {@code SaTokenContextException: SaTokenContext 上下文尚未初始化}（SSE 端点越慢越易触发）。
     *
     * <p>故这里只放行 {@code ASYNC}（含 {@code ERROR}/{@code FORWARD}）等非初始 dispatch：权限已在初始请求校验过，
     * 无需、也无法在异步回归时重复校验。初始请求仍走标准 {@link SaInterceptor} 逻辑，鉴权强度不变。
     */
    private static class AsyncAwareSaInterceptor extends SaInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            if (request.getDispatcherType() != DispatcherType.REQUEST) {
                return true;
            }
            return super.preHandle(request, response, handler);
        }
    }
}
