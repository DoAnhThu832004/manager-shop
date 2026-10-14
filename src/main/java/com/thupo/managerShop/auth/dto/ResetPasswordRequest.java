package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO đặt lại mật khẩu bằng token nhận qua email.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token đặt lại mật khẩu không được để trống")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu mới phải từ 8 đến 128 ký tự")
        String newPassword
) {
}
