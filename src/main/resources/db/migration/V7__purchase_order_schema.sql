-- =====================================================================
-- V7__purchase_order_schema.sql : Module Nhà cung cấp & Đơn đặt hàng nhập (Supplier, PurchaseOrder, Receipts)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

-- 1. Bảng suppliers
CREATE TABLE suppliers (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính nhà cung cấp',
    name         VARCHAR(255) NOT NULL COMMENT 'Tên nhà cung cấp',
    email        VARCHAR(255) NULL COMMENT 'Email liên hệ',
    phone        VARCHAR(50)  NULL COMMENT 'Số điện thoại liên hệ',
    address      VARCHAR(500) NULL COMMENT 'Địa chỉ nhà cung cấp',
    contact_name VARCHAR(255) NULL COMMENT 'Người liên hệ đại diện',
    deleted_at   DATETIME(3)  NULL COMMENT 'Thời gian xóa mềm',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_suppliers_name (name),
    KEY idx_suppliers_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Danh mục nhà cung cấp hàng hóa';

-- 2. Bảng purchase_orders
CREATE TABLE purchase_orders (
    id                      BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính đơn đặt hàng',
    po_number               VARCHAR(50)    NOT NULL COMMENT 'Mã đơn đặt hàng dạng PO-000001',
    supplier_id             BIGINT         NOT NULL COMMENT 'FK tới suppliers',
    destination_location_id BIGINT         NOT NULL COMMENT 'FK tới branches (kho đích nhận hàng)',
    reference_number        VARCHAR(255)   NULL COMMENT 'Số tham chiếu / hóa đơn ngoài',
    note_to_supplier        TEXT           NULL COMMENT 'Ghi chú cho nhà cung cấp',
    payment_terms           VARCHAR(100)   NULL COMMENT 'Điều khoản thanh toán',
    currency                VARCHAR(10)    NOT NULL DEFAULT 'VND' COMMENT 'Loại tiền tệ',
    tags                    JSON           NULL COMMENT 'Nhãn gắn đơn nhập hàng',
    status                  VARCHAR(30)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED',
    subtotal_cost           DECIMAL(19, 2) NOT NULL DEFAULT 0.00 COMMENT 'Tổng tiền hàng trước thuế',
    tax_total               DECIMAL(19, 2) NOT NULL DEFAULT 0.00 COMMENT 'Tổng tiền thuế',
    total_cost              DECIMAL(19, 2) NOT NULL DEFAULT 0.00 COMMENT 'Tổng chi phí đơn đặt hàng (subtotal + tax)',
    ordered_at              DATETIME(3)    NULL COMMENT 'Thời gian chốt đặt hàng (chuyển sang ORDERED)',
    version                 BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_number (po_number),
    KEY idx_po_supplier_id (supplier_id),
    KEY idx_po_destination_location (destination_location_id),
    KEY idx_po_status (status),
    KEY idx_po_created_at (created_at),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_po_destination_location FOREIGN KEY (destination_location_id) REFERENCES branches (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đơn đặt hàng nhập từ nhà cung cấp';

-- 3. Bảng purchase_order_items
CREATE TABLE purchase_order_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính dòng đặt hàng',
    purchase_order_id BIGINT         NOT NULL COMMENT 'FK tới purchase_orders',
    variant_id        BIGINT         NOT NULL COMMENT 'FK tới product_variants',
    quantity_ordered  INT            NOT NULL COMMENT 'Số lượng đặt (> 0)',
    quantity_received INT            NOT NULL DEFAULT 0 COMMENT 'Số lượng đã nhận (>= 0 và <= quantity_ordered)',
    unit_cost         DECIMAL(19, 2) NOT NULL DEFAULT 0.00 COMMENT 'Đơn giá nhập',
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_item_po_variant (purchase_order_id, variant_id),
    KEY idx_po_item_variant (variant_id),
    CONSTRAINT fk_po_item_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_item_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT chk_po_item_qty_ordered CHECK (quantity_ordered > 0),
    CONSTRAINT chk_po_item_qty_received CHECK (quantity_received >= 0 AND quantity_received <= quantity_ordered),
    CONSTRAINT chk_po_item_unit_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chi tiết các dòng hàng trong đơn đặt hàng nhập';

-- 4. Bảng purchase_order_receipts
CREATE TABLE purchase_order_receipts (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính phiếu nhận hàng',
    purchase_order_id BIGINT        NOT NULL COMMENT 'FK tới purchase_orders',
    received_by       BIGINT        NULL COMMENT 'FK tới users người nhận',
    received_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Thời điểm nhận hàng',
    idempotency_key   VARCHAR(100)  NOT NULL COMMENT 'Khóa idempotency đảm bảo không nhận trùng',
    note              VARCHAR(1000) NULL COMMENT 'Ghi chú lần nhận hàng',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_receipt_idempotency (idempotency_key),
    KEY idx_po_receipt_order (purchase_order_id),
    CONSTRAINT fk_po_receipt_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_receipt_user FOREIGN KEY (received_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phiếu nhận hàng thực tế theo đơn đặt hàng nhập';

-- 5. Bảng purchase_order_receipt_items
CREATE TABLE purchase_order_receipt_items (
    id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính dòng nhận hàng',
    receipt_id BIGINT NOT NULL COMMENT 'FK tới purchase_order_receipts',
    item_id    BIGINT NOT NULL COMMENT 'FK tới purchase_order_items',
    quantity   INT    NOT NULL COMMENT 'Số lượng nhận trong lần này (> 0)',
    PRIMARY KEY (id),
    KEY idx_receipt_items_receipt (receipt_id),
    KEY idx_receipt_items_item (item_id),
    CONSTRAINT fk_receipt_item_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_order_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_item_item FOREIGN KEY (item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT chk_receipt_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chi tiết số lượng các mặt hàng đã nhận trong phiếu';
