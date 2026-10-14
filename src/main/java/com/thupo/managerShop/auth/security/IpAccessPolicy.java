package com.thupo.managerShop.auth.security;

/**
 * Quyết định một IP có bị chặn hay không. {@link IpBlockFilter} gọi interface này trên mọi request nên
 * implementation phải trả lời nhanh (dùng cache trong bộ nhớ, không query DB mỗi lần).
 */
public interface IpAccessPolicy {

    /** IP trong whitelist không bao giờ bị chặn; IP trong blocklist còn hạn thì bị chặn. */
    boolean isBlocked(String clientIp);

    /** Số giây còn lại của lệnh chặn để trả header Retry-After; {@code null} nếu chặn vĩnh viễn/không rõ. */
    default Long retryAfterSeconds(String clientIp) {
        return null;
    }
}
