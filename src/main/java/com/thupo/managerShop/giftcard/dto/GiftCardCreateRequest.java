package com.thupo.managerShop.giftcard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request tạo thẻ quà tặng mới.
 * Nếu code để trống, hệ thống sẽ tự động sinh mã 16 ký tự an toàn.
 */
public record GiftCardCreateRequest(
        String code,

        @NotNull(message = "Mệnh giá ban đầu không được để trống")
        @DecimalMin(value = "0.01", message = "Mệnh giá ban đầu phải lớn hơn 0")
        BigDecimal initialValue,

        Instant expiresAt,

        Long customerId,

        @Size(max = 5000, message = "Ghi chú không được vượt quá 5000 ký tự")
        String notes
) {
}
