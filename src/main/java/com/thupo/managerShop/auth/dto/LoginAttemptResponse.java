package com.thupo.managerShop.auth.dto;

import java.time.Instant;

/**
 * DTO nhật ký nỗ lực đăng nhập dành cho trang quản trị Admin.
 */
public record LoginAttemptResponse(
        Long id,
        String usernameAttempted,
        String ipAddress,
        boolean success,
        String failureReason,
        String userAgent,
        Instant createdAt
) {
}
