package com.nebula.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.nebula.common.mybatis.config.MybatisPlusConfig;
import com.nebula.common.mybatis.handler.AuditMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * MyBatis-Plus 模块自动装配入口
 * 引入 nebula-starter-mybatis 后自动生效
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.MybatisConfiguration")
@Import(MybatisPlusConfig.class)
public class MybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
