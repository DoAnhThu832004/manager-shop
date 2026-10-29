package com.thupo.managerShop.supplychain.entity;

/**
 * Trạng thái của đơn chuyển kho nội bộ (Inventory Transfer).
 * Vòng đời: DRAFT -> IN_TRANSIT -> PARTIALLY_RECEIVED -> COMPLETED
 * Hoặc hủy: DRAFT / IN_TRANSIT -> CANCELLED
 */
public enum TransferStatus {
    DRAFT,
    IN_TRANSIT,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED
}
