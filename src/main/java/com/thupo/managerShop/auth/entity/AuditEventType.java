package com.thupo.managerShop.auth.entity;

/** Các sự kiện bảo mật cần ghi audit log. */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REGISTER_SUCCESS,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    IP_BLOCKED,
    IP_UNBLOCKED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    REFRESH_TOKEN_REUSE_DETECTED,
    SESSION_REVOKED,
    LOGOUT_ALL
}
