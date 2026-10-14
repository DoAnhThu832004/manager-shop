package com.thupo.managerShop.auth.dto;

import java.time.Instant;

/**
 * DTO thông tin phiên đăng nhập đang hoạt động của người dùng.
 */
public record SessionResponse(
        Long id,
        String deviceInfo,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        boolean current
) {
}
