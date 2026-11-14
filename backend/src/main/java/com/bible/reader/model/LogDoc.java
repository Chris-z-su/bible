package com.bible.reader.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * ES 日志文档。字段名与 mapping 严格一致。
 */
@Data
public class LogDoc {

    @JsonProperty("@timestamp")
    private String timestamp;
    private String level;
    private String event;
    private String message;
    private String path;
    private String method;
    private Integer status;
    private Long durationMs;
    private String ip;
    private String userAgent;
    private String query;
    private Integer bookId;
    private Integer chapter;
    private Integer verse;
    private String detail;
    private String app;
}
