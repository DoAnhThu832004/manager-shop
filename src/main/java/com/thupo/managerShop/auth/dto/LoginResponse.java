package com.thupo.managerShop.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO phản hồi khi đăng nhập thành công.
 * Cung cấp token, thời hạn, danh sách quyền và thông tin chi nhánh trực thuộc.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> roles,
        Long branchId,
        String branchName,
        boolean captchaRequired
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, List<String> roles, boolean captchaRequired) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, roles, null, null, captchaRequired);
    }

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, List<String> roles,
                                  Long branchId, String branchName, boolean captchaRequired) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, roles, branchId, branchName, captchaRequired);
    }
}
