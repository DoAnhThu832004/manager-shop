package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO đổi mật khẩu khi người dùng đã đăng nhập.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu mới phải từ 8 đến 128 ký tự")
        String newPassword
) {
}
