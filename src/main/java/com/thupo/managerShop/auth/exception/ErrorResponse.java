package com.thupo.managerShop.auth.exception;

import java.time.Instant;

/** Format lỗi thống nhất cho toàn bộ API auth. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Long retryAfterSeconds,
        String path) {
}
