package com.nebula.common.web.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 序列化定制
 * - LocalDateTime 用 yyyy-MM-dd HH:mm:ss
 * - Long / long 转字符串，防止前端 JS 精度丢失（雪花 ID 是 19 位长整型）
 *
 * @author nebula
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public JsonMapperBuilderCustomizer nebulaJacksonCustomizer() {
        return builder -> {
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
            DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
            DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_PATTERN);

            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(date));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(date));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(time));
            module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(time));
            module.addSerializer(Long.class, LongToStringSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, LongToStringSerializer.INSTANCE);

            builder.addModule(module);
        };
    }

    private static final class LongToStringSerializer extends ValueSerializer<Long> {

        static final LongToStringSerializer INSTANCE = new LongToStringSerializer();

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(String.valueOf(value));
            }
        }
    }
}
