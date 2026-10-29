package com.thupo.managerShop.supplychain.entity;

/**
 * Loại biến động tồn kho (sổ cái).
 */
public enum MovementType {
    ADJUSTMENT,
    PO_RECEIVE,
    TRANSFER_OUT,
    TRANSFER_IN,
    SALE,
    RETURN,
    CORRECTION
}
