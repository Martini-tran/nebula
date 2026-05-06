package com.nebula.common.web;

import com.nebula.common.web.config.JacksonConfig;
import com.nebula.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Web 模块自动装配入口
 * 引入 nebula-starter-web 后自动生效（仅 Servlet Web 应用）
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
public class WebAutoConfiguration {
}
