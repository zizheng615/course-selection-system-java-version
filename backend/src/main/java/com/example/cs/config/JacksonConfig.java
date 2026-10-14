package com.example.cs.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间的 JSON 序列化格式。
 *
 * <p>为什么需要这个类：{@code spring.jackson.date-format} 只作用于
 * {@code java.util.Date}，对 Java 8 时间类型（{@code LocalDateTime} / {@code LocalDate}）
 * 无效 —— 它们由 JavaTimeModule 按 ISO-8601 输出，形如 {@code 2026-09-27T22:47:55}。
 * 那个 T 分隔符直接显示在表格里既占宽度又不好读，所以在这里统一成
 * {@code yyyy-MM-dd HH:mm:ss} / {@code yyyy-MM-dd}。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeFormatCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE));
            builder.modules(module);
        };
    }
}
