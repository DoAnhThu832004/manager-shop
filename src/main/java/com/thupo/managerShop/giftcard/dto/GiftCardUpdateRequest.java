package com.thupo.managerShop.giftcard.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request cập nhật thẻ quà tặng.
 * Chỉ cho phép cập nhật expiresAt, customerId và notes. Không sửa số dư hay mã thẻ.
 */
public record GiftCardUpdateRequest(
        Instant expiresAt,

        Long customerId,

        @Size(max = 5000, message = "Ghi chú không được vượt quá 5000 ký tự")
        String notes
) {
}
