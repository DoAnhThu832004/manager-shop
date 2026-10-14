package com.thupo.managerShop.auth.ratelimit;

import java.time.Duration;

/**
 * Interface lưu trữ và kiểm tra rate limit, bộ đếm lỗi theo IP.
 * Thiết kế trừu tượng để dễ dàng chuyển đổi giữa Redis (mặc định cho multi-instance)
 * và MySQL/In-memory (dự phòng cho dev/test).
 */
public interface RateLimitStore {

    /**
     * Kiểm tra và tiêu thụ 1 token rate limit theo khóa và cửa sổ thời gian.
     *
     * @param key   Khóa định danh (ví dụ "login:ip:1.2.3.4")
     * @param limit Số lượng request tối đa trong cửa sổ
     * @param window Độ dài cửa sổ thời gian
     * @return true nếu request được phép đi qua, false nếu vượt quá giới hạn
     */
    boolean tryAcquire(String key, int limit, Duration window);

    /**
     * Ghi nhận 1 lần đăng nhập thất bại từ IP và username cụ thể.
     *
     * @param ip       IP của client
     * @param username Tên đăng nhập được thử
     * @param window   Cửa sổ thống kê (ví dụ 10 phút)
     */
    void recordIpFailure(String ip, String username, Duration window);

    /**
     * Lấy tổng số lần thất bại từ IP trong cửa sổ thời gian.
     */
    long getIpFailureCount(String ip, Duration window);

    /**
     * Lấy số lượng username khác nhau mà IP này đã thử trong cửa sổ thời gian.
     */
    long getIpDistinctUsernameCount(String ip, Duration window);

    /**
     * Reset bộ đếm thất bại của IP (dùng khi admin gỡ chặn hoặc IP được xác thực hợp lệ).
     */
    void resetIp(String ip);
}
