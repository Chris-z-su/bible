package com.bible.reader.config;

import com.bible.reader.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
            logService.recordApi(request, status, durationMs);
        } catch (Exception e) {
            log.debug("记录请求日志失败", e);
        }
    }
}
