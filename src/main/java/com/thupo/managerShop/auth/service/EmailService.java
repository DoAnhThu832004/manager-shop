package com.thupo.managerShop.auth.service;

import java.time.Instant;

/**
 * Interface gửi email thông báo các sự kiện bảo mật.
 * Mọi thao tác gửi email phải được thực hiện bất đồng bộ (@Async)
 * để không ảnh hưởng đến độ trễ và tính ổn định của luồng đăng nhập.
 */
public interface EmailService {

    /**
     * Gửi email cảnh báo khi tài khoản bị khoá do nhập sai mật khẩu nhiều lần liên tiếp.
     */
    void sendAccountLockedAlert(String recipientEmail, String username, Instant lockUntil, String ip);

    /**
     * Gửi email thông báo phát hiện đăng nhập từ IP hoặc thiết bị mới lạ.
     */
    void sendNewDeviceLoginAlert(String recipientEmail, String username, String ip, String deviceInfo);

    /**
     * Gửi email liên kết đặt lại mật khẩu kèm token dùng 1 lần.
     */
    void sendPasswordResetLink(String recipientEmail, String username, String resetToken);

    /**
     * Gửi email thông báo mật khẩu đã được thay đổi thành công.
     */
    void sendPasswordChangedAlert(String recipientEmail, String username, String ip);
}
