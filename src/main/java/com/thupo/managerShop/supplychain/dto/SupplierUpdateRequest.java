package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 255, message = "Tên nhà cung cấp không vượt quá 255 ký tự")
        String name,

        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email không vượt quá 255 ký tự")
        String email,

        @Size(max = 50, message = "Số điện thoại không vượt quá 50 ký tự")
        String phone,

        @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
        String address,

        @Size(max = 255, message = "Tên người liên hệ không vượt quá 255 ký tự")
        String contactName
) {}
