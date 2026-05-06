package com.nebula.common.redis;

import com.nebula.common.redis.config.RedisJsonMapperConfig;
import com.nebula.common.redis.util.RedisUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * nebula-sdk-redis 自动装配入口。
 * 引入 nebula-starter-redis（或本模块）后自动生效。
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnClass(RedisOperations.class)
@Import(RedisJsonMapperConfig.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisUtils redisUtils(StringRedisTemplate stringRedisTemplate,
                                 @Qualifier(RedisJsonMapperConfig.REDIS_JSON_MAPPER) JsonMapper redisJsonMapper) {
        return new RedisUtils(stringRedisTemplate, redisJsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
