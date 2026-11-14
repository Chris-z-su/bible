package com.bible.reader.service;

import com.bible.reader.common.BizException;
import com.bible.reader.dto.LogEventDto;
import com.bible.reader.dto.LogSearchResultDto;
import com.bible.reader.model.LogDoc;
import com.bible.reader.model.LogQuery;
import com.bible.reader.util.TextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * 日志服务：构造审计文档、异步提交、检索代理。
 */
@Slf4j
@Service
public class LogService {

    public static final Set<String> EVENT_WHITELIST = Set.of(
            "read", "search", "copy", "jump", "page_view", "theme", "font", "error", "api");
    public static final Set<String> LEVEL_WHITELIST = Set.of("INFO", "WARN", "ERROR");

    private static final int STATUS_ACCEPTED = 202;

    private final EsLogClient esLogClient;
    private final ThreadPoolTaskExecutor logExecutor;

    public LogService(EsLogClient esLogClient,
                      @Qualifier("logExecutor") ThreadPoolTaskExecutor logExecutor) {
        this.esLogClient = esLogClient;
        this.logExecutor = logExecutor;
    }

    /**
     * 请求日志拦截器调用：event=api。
     */
    public void recordApi(HttpServletRequest request, int status, long durationMs) {
        try {
            LogDoc doc = new LogDoc();
            doc.setTimestamp(TextUtils.nowIso());
            doc.setLevel(status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO");
            doc.setEvent("api");
            String uri = request.getRequestURI();
            doc.setMessage(request.getMethod() + " " + uri + " " + status + " " + durationMs + "ms");
            doc.setPath(uri);
            doc.setMethod(request.getMethod());
            doc.setStatus(status);
            doc.setDurationMs(durationMs);
            doc.setIp(clientIp(request));
            doc.setUserAgent(TextUtils.truncate(request.getHeader("User-Agent"), 512));
            doc.setQuery(TextUtils.truncate(request.getQueryString(), 512));
            doc.setApp("bible-reader");
            submit(doc);
        } catch (Exception e) {
            log.debug("构造请求日志失败", e);
        }
    }

    /**
     * 前端行为审计事件，异步写 ES，不阻塞请求。
     */
    public void acceptFrontend(LogEventDto event, HttpServletRequest request) {
        String eventName = event.getEvent() == null ? "" : event.getEvent().trim().toLowerCase(Locale.ROOT);
        if (!EVENT_WHITELIST.contains(eventName)) {
            throw BizException.badRequest("不支持的事件类型: " + eventName);
        }
        String level = normalizeLevel(event.getLevel(), eventName);
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            message = eventName;
        }

        LogDoc doc = new LogDoc();
        doc.setTimestamp(TextUtils.nowIso());
        doc.setLevel(level);
        doc.setEvent(eventName);
        doc.setMessage(message);
        doc.setPath(request.getRequestURI());
        doc.setMethod(request.getMethod());
        doc.setStatus(STATUS_ACCEPTED);
        doc.setDurationMs(0L);
        doc.setIp(clientIp(request));
        doc.setUserAgent(TextUtils.truncate(request.getHeader("User-Agent"), 512));
        doc.setQuery(event.getQuery());
        doc.setBookId(event.getBookId());
        doc.setChapter(event.getChapter());
        doc.setVerse(event.getVerse());
        doc.setDetail(event.getDetail());
        doc.setApp("bible-reader");
        submit(doc);
    }

    public LogSearchResultDto search(LogQuery query) {
        return esLogClient.search(query);
    }

    private String normalizeLevel(String rawLevel, String eventName) {
        if (rawLevel != null && !rawLevel.isBlank()) {
            String level = rawLevel.trim().toUpperCase(Locale.ROOT);
            if (!LEVEL_WHITELIST.contains(level)) {
                throw BizException.badRequest("level 只能是 INFO/WARN/ERROR");
            }
            return level;
        }
        return "error".equals(eventName) ? "ERROR" : "INFO";
    }

    private void submit(LogDoc doc) {
        try {
            logExecutor.execute(() -> esLogClient.index(doc));
        } catch (Exception e) {
            // 队列满时 RejectedExecutionHandler 已计数并丢弃，这里只防御线程池关闭等异常
            log.debug("日志异步提交失败: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return TextUtils.truncate((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return TextUtils.truncate(realIp.trim(), 64);
        }
        return TextUtils.truncate(request.getRemoteAddr(), 64);
    }
}
