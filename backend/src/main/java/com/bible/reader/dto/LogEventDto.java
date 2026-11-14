package com.bible.reader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端行为审计事件请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEventDto {

    @NotBlank(message = "event 不能为空")
    private String event;

    @Size(max = 500, message = "message 最长 500 字符")
    private String message;

    private Integer bookId;
    private Integer chapter;
    private Integer verse;

    @Size(max = 200, message = "query 最长 200 字符")
    private String query;

    private String detail;

    /** 前端可携带，服务端会重新校验；缺省时按 event 推断。 */
    private String level;
}
