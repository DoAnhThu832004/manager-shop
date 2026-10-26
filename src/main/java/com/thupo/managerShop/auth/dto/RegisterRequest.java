package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đăng ký tài khoản mới cho cửa hàng / nhân viên.
 */
public record RegisterRequest(
        @NotBlank(message = "Tên cửa hàng hoặc họ tên không được để trống")
        @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
        String fullName,

        @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
        @Size(max = 190, message = "Định danh không được vượt quá 190 ký tự")
        String identifier,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
        String password
) {
}
