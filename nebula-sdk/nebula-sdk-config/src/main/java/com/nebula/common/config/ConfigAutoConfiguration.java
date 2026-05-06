package com.nebula.common.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.common.config.listener.SysConfigRefreshListener;
import com.nebula.common.config.listener.SysConfigSubscriberRegistrar;
import com.nebula.common.config.loader.SysConfigCacheLoader;
import com.nebula.common.config.mapper.SysConfigMapper;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.config.service.impl.SysConfigServiceImpl;
import com.nebula.common.redis.RedisAutoConfiguration;
import com.nebula.common.redis.config.RedisJsonMapperConfig;
import com.nebula.common.redis.util.RedisUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 公共配置模块自动装配
 *
 * @author nebula
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({RedisOperations.class, BaseMapper.class})
@MapperScan("com.nebula.common.config.mapper")
public class ConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SysConfigService sysConfigService(SysConfigMapper mapper,
                                             RedisUtils redis,
                                             @Qualifier(RedisJsonMapperConfig.REDIS_JSON_MAPPER) JsonMapper jsonMapper,
                                             ApplicationEventPublisher events) {
        return new SysConfigServiceImpl(mapper, redis, jsonMapper, events);
    }

    @Bean
    @ConditionalOnMissingBean
    public SysConfigCacheLoader sysConfigCacheLoader(SysConfigService sysConfigService) {
        return new SysConfigCacheLoader((SysConfigServiceImpl) sysConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SysConfigRefreshListener sysConfigRefreshListener(ApplicationEventPublisher events) {
        return new SysConfigRefreshListener(events);
    }

    @Bean
    @ConditionalOnMissingBean
    public SysConfigSubscriberRegistrar sysConfigSubscriberRegistrar(RedisMessageListenerContainer container,
                                                                     SysConfigRefreshListener listener) {
        return new SysConfigSubscriberRegistrar(container, listener);
    }
}
