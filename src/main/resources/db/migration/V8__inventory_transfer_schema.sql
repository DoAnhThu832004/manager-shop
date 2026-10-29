-- =====================================================================
-- V8__inventory_transfer_schema.sql : Module Chuyển kho nội bộ (InventoryTransfer)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

-- 1. Bảng inventory_transfers
CREATE TABLE inventory_transfers (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính chuyển kho',
    transfer_number          VARCHAR(50)  NOT NULL COMMENT 'Mã chuyển kho dạng TR-000001',
    origin_location_id      BIGINT       NOT NULL COMMENT 'FK tới branches (kho xuất phát)',
    destination_location_id BIGINT       NOT NULL COMMENT 'FK tới branches (kho nhận đích)',
    transfer_date            DATETIME(3)  NULL COMMENT 'Ngày chuyển dự kiến hoặc thực tế',
    reference_name           VARCHAR(255) NULL COMMENT 'Tên tham chiếu chuyển kho',
    note                     TEXT         NULL COMMENT 'Ghi chú đơn chuyển kho',
    tags                     JSON         NULL COMMENT 'Nhãn phân loại chuyển kho',
    linked_purchase_order_id BIGINT       NULL COMMENT 'FK tới purchase_orders (nếu có liên kết)',
    status                   VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, IN_TRANSIT, PARTIALLY_RECEIVED, COMPLETED, CANCELLED',
    version                  BIGINT       NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfers_number (transfer_number),
    KEY idx_transfers_origin (origin_location_id),
    KEY idx_transfers_dest (destination_location_id),
    KEY idx_transfers_linked_po (linked_purchase_order_id),
    KEY idx_transfers_status (status),
    KEY idx_transfers_created_at (created_at),
    CONSTRAINT fk_transfers_origin FOREIGN KEY (origin_location_id) REFERENCES branches (id),
    CONSTRAINT fk_transfers_dest FOREIGN KEY (destination_location_id) REFERENCES branches (id),
    CONSTRAINT fk_transfers_linked_po FOREIGN KEY (linked_purchase_order_id) REFERENCES purchase_orders (id) ON DELETE SET NULL,
    CONSTRAINT chk_transfers_locations CHECK (origin_location_id <> destination_location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đơn chuyển hàng nội bộ giữa các chi nhánh kho';

-- 2. Bảng inventory_transfer_items
CREATE TABLE inventory_transfer_items (
    id                   BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính dòng chuyển kho',
    transfer_id          BIGINT NOT NULL COMMENT 'FK tới inventory_transfers',
    variant_id           BIGINT NOT NULL COMMENT 'FK tới product_variants',
    quantity_transferred INT    NOT NULL COMMENT 'Số lượng cần chuyển (> 0)',
    quantity_received    INT    NOT NULL DEFAULT 0 COMMENT 'Số lượng đã nhận (>= 0 và <= quantity_transferred)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_item_var (transfer_id, variant_id),
    KEY idx_transfer_item_var (variant_id),
    CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (transfer_id) REFERENCES inventory_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_item_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT chk_transfer_qty_transferred CHECK (quantity_transferred > 0),
    CONSTRAINT chk_transfer_qty_received CHECK (quantity_received >= 0 AND quantity_received <= quantity_transferred)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chi tiết các mặt hàng trong đơn chuyển kho';

-- 3. Bảng inventory_transfer_receipts
CREATE TABLE inventory_transfer_receipts (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính phiếu nhận hàng chuyển kho',
    transfer_id     BIGINT        NOT NULL COMMENT 'FK tới inventory_transfers',
    received_by     BIGINT        NULL COMMENT 'FK tới users người nhận',
    received_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Thời điểm nhận hàng',
    idempotency_key VARCHAR(100)  NOT NULL COMMENT 'Khóa idempotency đảm bảo không nhận trùng',
    note            VARCHAR(1000) NULL COMMENT 'Ghi chú lần nhận',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_receipt_idempotency (idempotency_key),
    KEY idx_transfer_receipt_transfer (transfer_id),
    CONSTRAINT fk_transfer_receipt_transfer FOREIGN KEY (transfer_id) REFERENCES inventory_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_receipt_user FOREIGN KEY (received_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phiếu nhận hàng thực tế theo đơn chuyển kho';

-- 4. Bảng inventory_transfer_receipt_items
CREATE TABLE inventory_transfer_receipt_items (
    id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính dòng nhận hàng chuyển kho',
    receipt_id BIGINT NOT NULL COMMENT 'FK tới inventory_transfer_receipts',
    item_id    BIGINT NOT NULL COMMENT 'FK tới inventory_transfer_items',
    quantity   INT    NOT NULL COMMENT 'Số lượng nhận trong lần này (> 0)',
    PRIMARY KEY (id),
    KEY idx_transfer_rcpt_items_rcpt (receipt_id),
    KEY idx_transfer_rcpt_items_item (item_id),
    CONSTRAINT fk_transfer_rcpt_item_rcpt FOREIGN KEY (receipt_id) REFERENCES inventory_transfer_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_rcpt_item_item FOREIGN KEY (item_id) REFERENCES inventory_transfer_items (id),
    CONSTRAINT chk_transfer_rcpt_item_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chi tiết các mặt hàng nhận trong phiếu chuyển kho';
