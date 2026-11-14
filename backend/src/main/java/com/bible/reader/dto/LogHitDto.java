package com.bible.reader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogHitDto {
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
    private Integer bookId;
    private Integer chapter;
    private Integer verse;

    /** 即使为 null 也输出，保持字段稳定。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String query;
}
