package com.thupo.managerShop.auth.entity;

/** Trạng thái refresh token: ACTIVE (dùng được), USED (đã xoay vòng), REVOKED (bị thu hồi). */
public enum RefreshTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
