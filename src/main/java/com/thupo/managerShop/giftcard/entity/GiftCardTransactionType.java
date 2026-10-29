package com.thupo.managerShop.giftcard.entity;

/**
 * Loại giao dịch ghi nhận trong sổ cái biến động thẻ quà tặng:
 * - ISSUE: Phát hành thẻ mới với số dư ban đầu.
 * - REDEEM: Khấu trừ số dư thẻ để thanh toán đơn hàng.
 * - REFUND: Hoàn lại số dư vào thẻ (ví dụ hủy đơn hàng).
 * - DISABLE: Vô hiệu hóa thẻ.
 */
public enum GiftCardTransactionType {
    ISSUE,
    REDEEM,
    REFUND,
    DISABLE
}
