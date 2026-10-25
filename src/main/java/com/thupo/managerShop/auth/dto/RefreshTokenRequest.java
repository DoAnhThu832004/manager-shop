package com.thupo.managerShop.auth.dto;

/**
 * DTO yêu cầu làm mới access token hoặc đăng xuất phiên.
 * Có thể bỏ trống nếu client Web gửi qua HttpOnly Cookie.
 */
public record RefreshTokenRequest(
        String refreshToken
) {
}
