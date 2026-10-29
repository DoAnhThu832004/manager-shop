package com.thupo.managerShop.giftcard.dto;

import com.thupo.managerShop.giftcard.entity.GiftCardTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response chi tiết cho một giao dịch trong sổ cái thẻ quà tặng.
 */
public record GiftCardTransactionResponse(
        Long id,
        GiftCardTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String orderRef,
        String idempotencyKey,
        Long createdById,
        Instant createdAt
) {
}
