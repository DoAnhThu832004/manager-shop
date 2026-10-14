package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu gửi liên kết đặt lại mật khẩu.
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Địa chỉ email không đúng định dạng")
        @Size(max = 190, message = "Email không được vượt quá 190 ký tự")
        String email
) {
}
