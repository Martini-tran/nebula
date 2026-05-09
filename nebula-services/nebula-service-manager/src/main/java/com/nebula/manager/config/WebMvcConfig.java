package com.nebula.manager.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
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
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register");
    }
}
