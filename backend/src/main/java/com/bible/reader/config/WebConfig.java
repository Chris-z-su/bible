package com.bible.reader.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS + 请求日志拦截器注册。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final RequestLogInterceptor requestLogInterceptor;

    public WebConfig(AppProperties properties, RequestLogInterceptor requestLogInterceptor) {
        this.properties = properties;
        this.requestLogInterceptor = requestLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/logs/search");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        String configured = properties.getCors().getAllowedOrigins();
        if (configured != null) {
            for (String origin : configured.split(",")) {
                if (!origin.isBlank()) {
                    origins.add(origin.trim());
                }
            }
        }
        if (origins.isEmpty()) {
            origins.add("*");
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
                .maxAge(3600);
    }
}
