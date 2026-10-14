package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO yêu cầu làm mới access token hoặc đăng xuất phiên.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
}
