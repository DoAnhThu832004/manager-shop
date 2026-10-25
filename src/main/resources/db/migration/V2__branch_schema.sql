-- =====================================================================
-- V2__branch_schema.sql : Bổ sung kiến trúc Đa chi nhánh (Multi-Branch)
-- =====================================================================

CREATE TABLE branches (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khoá chính chi nhánh',
    code        VARCHAR(50)  NOT NULL COMMENT 'Mã chi nhánh (CN-Q1, CN-Q7)',
    name        VARCHAR(100) NOT NULL COMMENT 'Tên chi nhánh',
    address     VARCHAR(255) NULL COMMENT 'Địa chỉ chi nhánh',
    phone       VARCHAR(20)  NULL COMMENT 'Số điện thoại liên hệ',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_branches_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chi nhánh cửa hàng';

-- Dữ liệu mẫu 2 chi nhánh ban đầu
INSERT INTO branches (id, code, name, address, phone, status) VALUES
(1, 'CN-Q1', 'Chi nhánh Quận 1 (Trụ sở)', '123 Lê Lợi, P. Bến Nghé, Quận 1, TP.HCM', '02812345678', 'ACTIVE'),
(2, 'CN-Q7', 'Chi nhánh Quận 7', '456 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM', '02887654321', 'ACTIVE');

-- Bổ sung branch_id vào bảng users (chi nhánh làm việc chính)
ALTER TABLE users ADD COLUMN branch_id BIGINT NULL COMMENT 'Chi nhánh trực thuộc chính' AFTER full_name;
ALTER TABLE users ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL;

-- Bảng quan hệ phân quyền nhiều chi nhánh (dành cho Quản lý / Chủ shop phụ trách nhiều chi nhánh)
CREATE TABLE user_branches (
    user_id   BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_id),
    KEY idx_ub_branch (branch_id),
    CONSTRAINT fk_ub_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ub_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phân quyền truy cập các chi nhánh cho user';
