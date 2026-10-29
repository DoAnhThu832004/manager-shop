package com.thupo.managerShop.giftcard.dto;

import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response trả về DUY NHẤT một lần khi tạo thẻ thành công (POST /api/v1/gift-cards).
 * Chứa mã thẻ gốc {@code code} để hiển thị cho khách hàng/quản trị viên lưu trữ.
 * Các API sau đó tuyệt đối không thể đọc lại mã này.
 */
public record GiftCardCreatedResponse(
        Long id,
        String code,
        String maskedCode,
        String codeLast4,
        BigDecimal initialValue,
        BigDecimal currentBalance,
        String currencyCode,
        Instant expiresAt,
        Long customerId,
        String notes,
        GiftCardStatus status,
        Long createdById,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        List<GiftCardTransactionResponse> transactions
) {
}
