-- =====================================================================
-- V9__gift_card_schema.sql : Module Thẻ quà tặng (GiftCard & Transactions)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- Lưu ý: Chưa có bảng customers, nên customer_id để BIGINT NULL không khóa ngoại.
-- =====================================================================

-- 1. Bảng gift_cards
CREATE TABLE gift_cards (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính thẻ quà tặng',
    code_hash       CHAR(64)       NOT NULL COMMENT 'HMAC-SHA256 của mã chuẩn hóa với secret pepper',
    code_last4      CHAR(4)        NOT NULL COMMENT '4 ký tự cuối phục vụ hiển thị maskedCode và tìm kiếm',
    initial_value   DECIMAL(19,2)  NOT NULL COMMENT 'Mệnh giá ban đầu của thẻ (> 0)',
    current_balance DECIMAL(19,2)  NOT NULL COMMENT 'Số dư khả dụng hiện tại (>= 0 và <= initial_value)',
    currency_code   VARCHAR(10)    NOT NULL DEFAULT 'VND' COMMENT 'Mã đơn vị tiền tệ (mặc định VND)',
    expires_at      DATETIME(3)    NULL COMMENT 'Thời điểm hết hạn (NULL là vĩnh viễn / Doesn''t expire)',
    customer_id     BIGINT         NULL COMMENT 'ID khách hàng sở hữu thẻ (chờ bảng customers)',
    notes           TEXT           NULL COMMENT 'Ghi chú nội bộ cho thẻ (tối đa 5000 ký tự)',
    status          VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISABLED, EXPIRED, DEPLETED',
    disabled_at     DATETIME(3)    NULL COMMENT 'Thời điểm vô hiệu hóa thẻ',
    created_by      BIGINT         NOT NULL COMMENT 'FK tới users (nhân viên/quản trị viên phát hành)',
    version         BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version chống double-spending',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_cards_code_hash (code_hash),
    KEY idx_gift_cards_code_last4 (code_last4),
    KEY idx_gift_cards_status (status),
    KEY idx_gift_cards_customer_id (customer_id),
    KEY idx_gift_cards_expires_at (expires_at),
    KEY idx_gift_cards_created_at (created_at),
    KEY idx_gift_cards_current_balance (current_balance),
    CONSTRAINT fk_gift_cards_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_gc_initial_val CHECK (initial_value > 0),
    CONSTRAINT chk_gc_current_bal CHECK (current_balance >= 0 AND current_balance <= initial_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Danh sách thẻ quà tặng đã phát hành';

-- 2. Bảng gift_card_transactions (Sổ cái biến động số dư thẻ, chỉ thêm / append-only)
CREATE TABLE gift_card_transactions (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính giao dịch thẻ quà tặng',
    gift_card_id    BIGINT        NOT NULL COMMENT 'FK tới gift_cards',
    type            VARCHAR(30)   NOT NULL COMMENT 'ISSUE, REDEEM, REFUND, DISABLE',
    amount          DECIMAL(19,2) NOT NULL COMMENT 'Số tiền biến động trong giao dịch',
    balance_after   DECIMAL(19,2) NOT NULL COMMENT 'Số dư khả dụng của thẻ ngay sau giao dịch',
    order_ref       VARCHAR(100)  NULL COMMENT 'Mã tham chiếu đơn hàng (nếu có)',
    idempotency_key VARCHAR(100)  NULL COMMENT 'Khóa idempotent đảm bảo không trừ / hoàn lặp',
    created_by      BIGINT        NOT NULL COMMENT 'FK tới users (người thực hiện thao tác)',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_gc_tx_idempotency (idempotency_key),
    KEY idx_gc_tx_gift_card_id (gift_card_id),
    KEY idx_gc_tx_type (type),
    KEY idx_gc_tx_created_at (created_at),
    CONSTRAINT fk_gc_tx_gift_card FOREIGN KEY (gift_card_id) REFERENCES gift_cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_tx_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sổ cái lịch sử giao dịch phát hành, thanh toán và hoàn tiền thẻ quà tặng';
