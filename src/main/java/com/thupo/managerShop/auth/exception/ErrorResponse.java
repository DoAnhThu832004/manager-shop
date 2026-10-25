package com.thupo.managerShop.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Format lỗi thống nhất cho toàn bộ API auth.
 * Trường captchaRequired báo cho frontend biết có cần hiển thị Captcha cho lần thử tiếp theo không.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Long retryAfterSeconds,
        String path,
        Boolean captchaRequired) {

    public ErrorResponse(Instant timestamp, int status, String code, String message, Long retryAfterSeconds, String path) {
        this(timestamp, status, code, message, retryAfterSeconds, path, null);
    }
}
