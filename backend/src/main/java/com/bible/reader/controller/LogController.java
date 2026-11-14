package com.bible.reader.controller;

import com.bible.reader.common.ApiResponse;
import com.bible.reader.common.BizException;
import com.bible.reader.dto.LogEventDto;
import com.bible.reader.dto.LogSearchResultDto;
import com.bible.reader.model.LogQuery;
import com.bible.reader.service.LogService;
import com.bible.reader.util.TextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeParseException;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<Void>> accept(@Valid @RequestBody LogEventDto event,
                                                    HttpServletRequest request) {
        logService.acceptFrontend(event, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted());
    }

    @GetMapping("/logs/search")
    public ApiResponse<LogSearchResultDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        LogQuery query = new LogQuery();
        if (q != null) {
            String keyword = q.trim();
            if (keyword.length() > 200) {
                throw BizException.badRequest("q 最长 200 字符");
            }
            query.setQ(keyword.isEmpty() ? null : keyword);
        }
        if (level != null && !level.isBlank()) {
            String normalized = level.trim().toUpperCase(Locale.ROOT);
            if (!LogService.LEVEL_WHITELIST.contains(normalized)) {
                throw BizException.badRequest("level 只能是 INFO/WARN/ERROR");
            }
            query.setLevel(normalized);
        }
        if (event != null && !event.isBlank()) {
            String normalized = event.trim().toLowerCase(Locale.ROOT);
            if (!LogService.EVENT_WHITELIST.contains(normalized)) {
                throw BizException.badRequest("不支持的事件类型: " + normalized);
            }
            query.setEvent(normalized);
        }
        try {
            query.setFrom(TextUtils.parseDateTime(from, false));
            query.setTo(TextUtils.parseDateTime(to, true));
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("时间格式无法识别，应为 ISO-8601 或 yyyy-MM-dd");
        }
        if (query.getFrom() != null && query.getTo() != null && query.getFrom().isAfter(query.getTo())) {
            throw BizException.badRequest("from 不能晚于 to");
        }
        if (page < 1) {
            throw BizException.badRequest("page 必须大于等于 1");
        }
        if (size < 1 || size > 100) {
            throw BizException.badRequest("size 必须在 1~100 之间");
        }
        query.setPage(page);
        query.setSize(size);

        return ApiResponse.ok(logService.search(query));
    }
}
