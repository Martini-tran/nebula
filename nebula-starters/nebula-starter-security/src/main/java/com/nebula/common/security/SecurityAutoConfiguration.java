package com.nebula.common.security;

import com.nebula.common.security.apikey.ApiKeyAccessInterceptor;
import com.nebula.common.security.apikey.ApiKeyExceptionHandler;
import com.nebula.common.security.filter.UserContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet security auto-configuration.
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserContextFilter.class)
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyAccessInterceptor.class)
    public ApiKeyAccessInterceptor apiKeyAccessInterceptor() {
        return new ApiKeyAccessInterceptor();
    }

    /**
     * 对所有路径注册 API Key 守卫，并排在最前：默认拒绝要先于任何业务拦截器生效。
     * 放在 starter 里是为了让每个 servlet 服务自动带上——只要有一个服务漏装，带 key 的请求在那里就不受约束。
     */
    @Bean
    public WebMvcConfigurer apiKeyAccessWebMvcConfigurer(ApiKeyAccessInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .order(Ordered.HIGHEST_PRECEDENCE);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyExceptionHandler.class)
    public ApiKeyExceptionHandler apiKeyExceptionHandler() {
        return new ApiKeyExceptionHandler();
    }
}
