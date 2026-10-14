package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đăng nhập.
 * Chấp nhận username hoặc email qua trường identifier.
 */
public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
        @Size(max = 190, message = "Định danh không được vượt quá 190 ký tự")
        String identifier,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 128, message = "Mật khẩu không được vượt quá 128 ký tự")
        String password,

        @Size(max = 255, message = "Thông tin thiết bị không được vượt quá 255 ký tự")
        String deviceInfo,

        String captchaToken
) {
}
