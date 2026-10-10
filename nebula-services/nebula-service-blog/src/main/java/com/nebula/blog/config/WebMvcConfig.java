package com.nebula.blog.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置
 * 注册 Sa-Token 拦截器，使控制器上的 {@code @SaCheckPermission} / {@code @SaCheckOr} / {@code @SaCheckApiKey}
 * 等注解真正生效。登录态校验主要由网关完成，这里兜底权限校验；没有任何鉴权注解的接口（如 {@code /front/**}）不受影响。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InitialDispatchSaInterceptor()).addPathPatterns("/**");
    }

    /**
     * 只在初始请求做注解校验。异步回归等后续 dispatch 时 Sa-Token 上下文可能已随初始请求清理，
     * 再校验会抛「上下文尚未初始化」；权限已在初始请求校验过，无需重复。
     */
    private static class InitialDispatchSaInterceptor extends SaInterceptor {

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
