package com.thupo.managerShop.auth.entity;

/** Trạng thái tài khoản. Khoá tạm thời dùng {@code lock_until}, LOCKED là khoá thủ công/vĩnh viễn. */
public enum UserStatus {
    ACTIVE,
    PENDING_APPROVAL,
    LOCKED,
    DISABLED
}
