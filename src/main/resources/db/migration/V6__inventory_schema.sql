-- =====================================================================
-- V6__inventory_schema.sql : Module Tồn kho đa điểm (InventoryLevel, InventoryMovement)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

-- 1. Bảng inventory_levels
CREATE TABLE inventory_levels (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    variant_id  BIGINT      NOT NULL COMMENT 'FK tới product_variants',
    location_id BIGINT      NOT NULL COMMENT 'FK tới branches (đóng vai trò kho/chi nhánh)',
    available   INT         NOT NULL DEFAULT 0 COMMENT 'Số lượng khả dụng có thể bán',
    on_hand     INT         NOT NULL DEFAULT 0 COMMENT 'Tổng số lượng thực tế trong kho (on_hand = available + committed + unavailable)',
    committed   INT         NOT NULL DEFAULT 0 COMMENT 'Số lượng đã cam kết trong đơn hàng chưa giao',
    unavailable INT         NOT NULL DEFAULT 0 COMMENT 'Số lượng tạm giữ / hư hỏng / trưng bày',
    incoming    INT         NOT NULL DEFAULT 0 COMMENT 'Số lượng đang trên đường nhập về',
    version     BIGINT      NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_level_var_loc (variant_id, location_id),
    KEY idx_inv_level_location (location_id),
    KEY idx_inv_level_variant (variant_id),
    CONSTRAINT fk_inv_level_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_level_location FOREIGN KEY (location_id) REFERENCES branches (id) ON DELETE CASCADE,
    CONSTRAINT chk_inv_available CHECK (available >= 0),
    CONSTRAINT chk_inv_on_hand CHECK (on_hand >= 0),
    CONSTRAINT chk_inv_committed CHECK (committed >= 0),
    CONSTRAINT chk_inv_unavailable CHECK (unavailable >= 0),
    CONSTRAINT chk_inv_incoming CHECK (incoming >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tồn kho của từng biến thể theo địa điểm';

-- 2. Bảng inventory_movements (Sổ cái biến động kho - Append-Only)
CREATE TABLE inventory_movements (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính log biến động',
    variant_id       BIGINT       NOT NULL COMMENT 'FK tới product_variants',
    location_id      BIGINT       NOT NULL COMMENT 'FK tới branches',
    movement_type    VARCHAR(30)  NOT NULL COMMENT 'ADJUSTMENT, PO_RECEIVE, TRANSFER_OUT, TRANSFER_IN, SALE, RETURN, CORRECTION',
    quantity_delta   INT          NOT NULL COMMENT 'Số lượng thay đổi (có dấu)',
    available_before INT          NOT NULL COMMENT 'Tồn khả dụng trước thay đổi',
    available_after  INT          NOT NULL COMMENT 'Tồn khả dụng sau thay đổi',
    on_hand_before   INT          NOT NULL COMMENT 'Tồn thực tế trước thay đổi',
    on_hand_after    INT          NOT NULL COMMENT 'Tồn thực tế sau thay đổi',
    reason           VARCHAR(255) NULL COMMENT 'Lý do thay đổi',
    reference_type   VARCHAR(100) NULL COMMENT 'Loại chứng từ liên quan (PO, TRANSFER, ORDER, MANUAL)',
    reference_id     VARCHAR(100) NULL COMMENT 'Mã chứng từ liên quan',
    created_by       BIGINT       NULL COMMENT 'FK tới users người thực hiện',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_inv_mov_var_loc_created (variant_id, location_id, created_at),
    KEY idx_inv_mov_created_at (created_at),
    KEY idx_inv_mov_user (created_by),
    CONSTRAINT fk_inv_mov_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_mov_location FOREIGN KEY (location_id) REFERENCES branches (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_mov_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sổ cái lịch sử biến động tồn kho';
