package com.thupo.managerShop.giftcard.dto;

import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response chi tiết thẻ quà tặng.
 * Tuyệt đối không chứa mã thẻ gốc; chỉ trả về maskedCode (ví dụ ************ab12) và codeLast4.
 */
public record GiftCardResponse(
        Long id,
        String maskedCode,
        String codeLast4,
        BigDecimal initialValue,
        BigDecimal currentBalance,
        String currencyCode,
        Instant expiresAt,
        Long customerId,
        String notes,
        GiftCardStatus status,
        Instant disabledAt,
        Long createdById,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        List<GiftCardTransactionResponse> transactions
) {
}
