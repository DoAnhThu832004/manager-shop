package com.thupo.managerShop.giftcard.entity;

/**
 * Trạng thái của thẻ quà tặng:
 * - ACTIVE: Đang hoạt động, có thể sử dụng thanh toán.
 * - DISABLED: Đã bị nhân viên/quản trị viên vô hiệu hóa, không thể sử dụng hay kích hoạt lại.
 * - EXPIRED: Đã quá hạn sử dụng (expires_at đã qua).
 * - DEPLETED: Đã tiêu hết số dư (current_balance = 0). Có thể kích hoạt lại thành ACTIVE nếu được hoàn tiền (refund).
 */
public enum GiftCardStatus {
    ACTIVE,
    DISABLED,
    EXPIRED,
    DEPLETED
}
