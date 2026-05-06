package com.nebula.common.redis.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Redis 内部使用的 JsonMapper，与 web 层暴露给前端的 JsonMapper 隔离：
 * - 保留 LocalDateTime / LocalDate / LocalTime 的字符串格式化（写入/读出可对称还原）
 * - 不做 Long → String 转换（缓存内部用，不需要 JS 精度兼容）
 *
 * @author nebula
 */
@Configuration
public class RedisJsonMapperConfig {

    public static final String REDIS_JSON_MAPPER = "redisJsonMapper";

    @Bean(REDIS_JSON_MAPPER)
    public JsonMapper redisJsonMapper() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss");

        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(date));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(date));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(time));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(time));

        return JsonMapper.builder().addModule(module).build();
    }
}
