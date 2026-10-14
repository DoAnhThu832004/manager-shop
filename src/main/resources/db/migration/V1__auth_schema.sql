-- =====================================================================
-- V1__auth_schema.sql : Module Authentication (MySQL 8, InnoDB, utf8mb4)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

CREATE TABLE users (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khoá chính',
    username           VARCHAR(50)  NOT NULL COMMENT 'Tên đăng nhập (lưu lowercase)',
    email              VARCHAR(190) NOT NULL COMMENT 'Email (lowercase)',
    password_hash      VARCHAR(100) NOT NULL COMMENT 'BCrypt hash',
    full_name          VARCHAR(100) NOT NULL COMMENT 'Họ tên',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LOCKED/DISABLED',
    failed_login_count INT          NOT NULL DEFAULT 0 COMMENT 'Số lần sai liên tiếp',
    lock_until         DATETIME(3)  NULL COMMENT 'Khoá tạm đến khi nào (UTC); NULL = không khoá',
    lock_level         INT          NOT NULL DEFAULT 0 COMMENT 'Số lần đã bị khoá (để khoá luỹ tiến)',
    token_version      INT          NOT NULL DEFAULT 0 COMMENT 'Tăng khi cần vô hiệu hoá mọi access token',
    last_login_at      DATETIME(3)  NULL,
    last_login_ip      VARCHAR(45)  NULL COMMENT 'IPv4/IPv6',
    deleted_at         DATETIME(3)  NULL COMMENT 'Xoá mềm',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version            BIGINT       NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),   -- login theo username
    UNIQUE KEY uk_users_email (email)          -- login theo email, forgot-password
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tài khoản';

CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL COMMENT 'OWNER/MANAGER/CASHIER/WAREHOUSE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vai trò RBAC';

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Gán role cho user';

INSERT INTO roles (name) VALUES ('OWNER'), ('MANAGER'), ('CASHIER'), ('WAREHOUSE');

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  CHAR(64)     NOT NULL COMMENT 'SHA-256 hex của refresh token',
    family_id   CHAR(36)     NOT NULL COMMENT 'UUID họ token của 1 phiên đăng nhập',
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/USED/REVOKED',
    expires_at  DATETIME(3)  NOT NULL,
    device_info VARCHAR(255) NULL,
    ip_address  VARCHAR(45)  NULL,
    user_agent  VARCHAR(500) NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    used_at     DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_token_hash (token_hash),            -- tra cứu token khi refresh
    KEY idx_rt_user_status (user_id, status),            -- list phiên, đếm phiên, logout-all
    KEY idx_rt_family (family_id),                       -- thu hồi cả họ khi reuse
    KEY idx_rt_expires (expires_at),                     -- job dọn dẹp
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh token (chỉ lưu hash)';

CREATE TABLE login_attempts (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    username_attempted VARCHAR(190) NULL,
    ip_address         VARCHAR(45)  NOT NULL,
    success            TINYINT(1)   NOT NULL,
    failure_reason     VARCHAR(50)  NULL,
    user_agent         VARCHAR(500) NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_la_ip_created (ip_address, created_at),            -- đếm theo IP trong cửa sổ thời gian
    KEY idx_la_username_created (username_attempted, created_at), -- đếm/xem theo username
    KEY idx_la_created (created_at)                            -- job dọn > 90 ngày, lọc theo thời gian
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Log đăng nhập, ghi theo lô bất đồng bộ';

CREATE TABLE ip_blocklist (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ip_address    VARCHAR(45)  NOT NULL,
    reason        VARCHAR(255) NULL,
    block_level   INT          NOT NULL DEFAULT 1 COMMENT 'Luỹ tiến: 1,2,3,4(vĩnh viễn)',
    blocked_until DATETIME(3)  NULL COMMENT 'NULL = vĩnh viễn',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by    VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ipb_ip (ip_address),                  -- 1 dòng/IP, tăng block_level khi tái phạm
    KEY idx_ipb_until (blocked_until)                   -- job dọn IP hết hạn
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP bị chặn';

CREATE TABLE ip_whitelist (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    ip_or_cidr VARCHAR(49) NOT NULL COMMENT 'IP hoặc CIDR, ví dụ 192.168.1.0/24',
    note      VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ipw_value (ip_or_cidr)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP không bao giờ bị chặn';

CREATE TABLE password_reset_tokens (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL COMMENT 'SHA-256 hex của token gửi qua email',
    expires_at   DATETIME(3) NOT NULL,
    used_at      DATETIME(3) NULL,
    requested_ip VARCHAR(45) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prt_token_hash (token_hash),          -- tra cứu khi reset
    KEY idx_prt_user_created (user_id, created_at),     -- giới hạn 3 yêu cầu/giờ, vô hiệu token cũ
    KEY idx_prt_ip_created (requested_ip, created_at),  -- giới hạn 10 yêu cầu/giờ/IP
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token đặt lại mật khẩu (chỉ lưu hash)';

CREATE TABLE password_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ph_user_created (user_id, created_at),      -- lấy 3 mật khẩu gần nhất
    CONSTRAINT fk_ph_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Lịch sử mật khẩu';

CREATE TABLE audit_logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL COMMENT 'Không FK để giữ log khi xoá user và ghi nhanh',
    event_type VARCHAR(50)  NOT NULL,
    severity   VARCHAR(10)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/HIGH',
    ip_address VARCHAR(45)  NULL,
    user_agent VARCHAR(500) NULL,
    detail     JSON         NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_al_user_created (user_id, created_at),
    KEY idx_al_event_created (event_type, created_at),
    KEY idx_al_created (created_at)                     -- job dọn dẹp
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Nhật ký sự kiện bảo mật';

-- Bảng khoá cho ShedLock (job dọn dẹp chạy an toàn khi nhiều instance)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
