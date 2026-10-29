package com.thupo.managerShop.giftcard.dto;

import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import java.math.BigDecimal;

/**
 * Kết quả thực thi phương thức nội bộ redeem hoặc refund thẻ quà tặng.
 */
public record GiftCardRedeemResult(
        Long giftCardId,
        BigDecimal transactionAmount,
        BigDecimal balanceAfter,
        GiftCardStatus status,
        String idempotencyKey
) {
}
