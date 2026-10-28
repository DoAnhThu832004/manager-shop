package com.thupo.managerShop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu đăng ký tài khoản người dùng mới.
 */
public record RegisterRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
        String fullName,

        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Tên đăng nhập chỉ được chứa chữ cái, chữ số và dấu gạch dưới")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 190, message = "Email không được vượt quá 190 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
        String password,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống")
        String confirmPassword
) {
    /**
     * Kiểm tra mật khẩu xác nhận có khớp với mật khẩu chính hay không.
     */
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
