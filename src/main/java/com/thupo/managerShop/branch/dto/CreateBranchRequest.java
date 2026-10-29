package com.thupo.managerShop.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO khởi tạo chi nhánh đầu tiên của cửa hàng.
 */
public record CreateBranchRequest(
        @NotBlank(message = "Mã chi nhánh không được để trống")
        @Size(min = 2, max = 50, message = "Mã chi nhánh phải từ 2 đến 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chi nhánh chỉ chứa chữ cái, số, dấu gạch nối và gạch dưới")
        String code,

        @NotBlank(message = "Tên chi nhánh không được để trống")
        @Size(max = 100, message = "Tên chi nhánh không được vượt quá 100 ký tự")
        String name,

        @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
        String address,

        @Pattern(regexp = "^[0-9+() -]*$", message = "Số điện thoại không hợp lệ")
        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        String phone
) {}
