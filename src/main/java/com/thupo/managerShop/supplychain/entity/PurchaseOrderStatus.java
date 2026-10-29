package com.thupo.managerShop.supplychain.entity;

/**
 * Trạng thái của đơn đặt hàng nhập (Purchase Order).
 * Vòng đời chuẩn: DRAFT -> ORDERED -> PARTIALLY_RECEIVED -> RECEIVED -> CLOSED
 * Hoặc hủy: DRAFT / ORDERED -> CANCELLED
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CLOSED,
    CANCELLED
}
