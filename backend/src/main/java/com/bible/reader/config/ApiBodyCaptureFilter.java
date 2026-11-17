package com.bible.reader.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 缓存 /api/** 的请求体与响应体，供 RequestLogInterceptor 记录入参、出参。
 * /api/logs/search 自身不记录，避免日志检索再产生日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiBodyCaptureFilter extends OncePerRequestFilter {

    /** 请求体最多缓存 8KB，足够覆盖本项目的 JSON 入参 */
    private static final int REQUEST_CACHE_LIMIT = 8 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/logs/search");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT);
        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // 缓存的内容必须回写到真实响应，否则客户端会收不到响应体
            wrappedResponse.copyBodyToResponse();
        }
    }
}
