package com.bible.reader.model;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 日志检索条件。时间已在入口解析为 +08:00 的 OffsetDateTime。
 */
@Data
public class LogQuery {
    private String q;
    private String level;
    private String event;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private int page = 1;
    private int size = 20;
}
