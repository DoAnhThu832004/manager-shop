package com.thupo.managerShop.auth.security;

/** Interface phát tín hiệu xoá cache người dùng trên toàn bộ các node trong cluster. */
public interface UserCacheBroadcaster {
    void broadcastEvict(Long userId);
}
