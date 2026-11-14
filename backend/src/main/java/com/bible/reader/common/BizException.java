package com.bible.reader.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态与响应 code。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BizException(int code, String message) {
        this(code, message, defaultStatus(code));
    }

    public BizException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message, HttpStatus.BAD_REQUEST);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message, HttpStatus.NOT_FOUND);
    }

    private static HttpStatus defaultStatus(int code) {
        if (code == 404) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
