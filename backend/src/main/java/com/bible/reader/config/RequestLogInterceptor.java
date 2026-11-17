package com.bible.reader.config;

import com.bible.reader.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 记录所有 /api/** 的访问日志（异步），/api/logs/search 自身已在 WebConfig 中排除。
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_ATTRIBUTE = RequestLogInterceptor.class.getName() + ".start";

    private final LogService logService;

    public RequestLogInterceptor(LogService logService) {
        this.logService = logService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Object start = request.getAttribute(START_ATTRIBUTE);
            long durationMs = start instanceof Long startedAt
                    ? Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)
                    : 0L;
            int status = response.getStatus();
            if (ex != null && status < 400) {
                status = 500;
            }
            logService.recordApi(request, status, durationMs,
                    readRequestBody(request), readResponseBody(response), responseSize(response));
        } catch (Exception e) {
            log.debug("记录请求日志失败", e);
        }
    }

    /**
     * 入参：仅记录文本类请求（JSON/表单），由 ApiBodyCaptureFilter 缓存。
     */
    private static String readRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)
                || !isTextual(wrapper.getContentType())) {
            return null;
        }
        byte[] bytes = wrapper.getContentAsByteArray();
        return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 出参：仅记录文本类响应（JSON/HTML 等），由 ApiBodyCaptureFilter 缓存。
     */
    private static String readResponseBody(HttpServletResponse response) {
        if (!(response instanceof ContentCachingResponseWrapper wrapper)
                || !isTextual(wrapper.getContentType())) {
            return null;
        }
        byte[] bytes = wrapper.getContentAsByteArray();
        return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static Integer responseSize(HttpServletResponse response) {
        return response instanceof ContentCachingResponseWrapper wrapper
                ? wrapper.getContentSize() : null;
    }

    private static boolean isTextual(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.contains("json") || type.contains("xml") || type.startsWith("text/");
    }
}
