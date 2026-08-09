package com.redtour.business.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * RestTemplate Bean 配置（Spring Boot 2.x+ 不再自动提供，AiEngineClient 构造器注入依赖）
 * - 连接超时 & 读取超时 从 application.yml 的 ai-engine.* 配置读取
 * - 统一使用 UTF-8 编码，避免中文响应乱码
 */
@Configuration
public class RestTemplateConfig {

    @Value("${ai-engine.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${ai-engine.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .additionalMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter()
                )
                .build();
        return restTemplate;
    }
}
