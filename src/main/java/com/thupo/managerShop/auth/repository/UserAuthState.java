package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.UserStatus;
import java.time.Instant;

/** Projection nhẹ chứa trạng thái dùng để kiểm tra access token (tránh load cả entity). */
public interface UserAuthState {

    UserStatus getStatus();

    int getTokenVersion();

    Instant getDeletedAt();
}
