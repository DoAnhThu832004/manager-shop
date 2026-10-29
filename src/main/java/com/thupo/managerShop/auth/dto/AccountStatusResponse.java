package com.thupo.managerShop.auth.dto;

import com.thupo.managerShop.auth.entity.UserStatus;

/**
 * Phản hồi trạng thái tài khoản cho endpoint kiểm tra nhanh (không cần token).
 */
public record AccountStatusResponse(
        String username,
        UserStatus status,
        String message
) {
    public static AccountStatusResponse of(String username, UserStatus status) {
        String msg = switch (status) {
            case ACTIVE -> "Tài khoản đã được phê duyệt và sẵn sàng đăng nhập";
            case PENDING_APPROVAL -> "Tài khoản đang chờ quản trị viên phê duyệt";
            case LOCKED -> "Tài khoản đang bị tạm khoá";
            case DISABLED -> "Tài khoản đã bị vô hiệu hoá";
        };
        return new AccountStatusResponse(username, status, msg);
    }
}
