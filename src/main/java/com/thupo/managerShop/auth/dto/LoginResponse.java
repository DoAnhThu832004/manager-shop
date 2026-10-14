package com.thupo.managerShop.auth.dto;

import java.util.List;

/**
 * DTO phản hồi khi đăng nhập thành công.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> roles,
        boolean captchaRequired
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, List<String> roles, boolean captchaRequired) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, roles, captchaRequired);
    }
}
