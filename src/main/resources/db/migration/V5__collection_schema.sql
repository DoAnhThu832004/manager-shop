-- =====================================================================
-- V5__collection_schema.sql : Module Bộ sưu tập (Collections, Rules, CollectionProducts)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

-- 1. Bảng collections
CREATE TABLE collections (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính bộ sưu tập',
    title                   VARCHAR(255)  NOT NULL COMMENT 'Tiêu đề bộ sưu tập',
    description             LONGTEXT      NULL COMMENT 'Mô tả chi tiết (HTML)',
    image_url               VARCHAR(1000) NULL COMMENT 'Ảnh đại diện bộ sưu tập',
    collection_type         VARCHAR(20)   NOT NULL COMMENT 'MANUAL hoặc AUTOMATED',
    rule_applies_disjunctive BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'false = khớp TẤT CẢ, true = khớp BẤT KỲ',
    deleted_at              DATETIME(3)   NULL COMMENT 'Xóa mềm',
    created_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collections_type (collection_type),
    KEY idx_collections_deleted_at (deleted_at),
    KEY idx_collections_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bộ sưu tập sản phẩm';

-- 2. Bảng collection_rules (chỉ dùng cho AUTOMATED)
CREATE TABLE collection_rules (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính quy tắc',
    collection_id BIGINT       NOT NULL COMMENT 'FK tới collections',
    field         VARCHAR(30)  NOT NULL COMMENT 'TITLE, TYPE, VENDOR, TAG, PRICE, INVENTORY_STOCK',
    operator      VARCHAR(30)  NOT NULL COMMENT 'EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, GREATER_THAN, LESS_THAN, STARTS_WITH, ENDS_WITH',
    value         VARCHAR(255) NOT NULL COMMENT 'Giá trị điều kiện so sánh',
    is_exclusion  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'true ứng với nút + Exclude',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collection_rules_col (collection_id),
    CONSTRAINT fk_collection_rules_col FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quy tắc điều kiện của bộ sưu tập tự động';

-- 3. Bảng collection_products (chỉ dùng cho MANUAL)
CREATE TABLE collection_products (
    collection_id BIGINT      NOT NULL COMMENT 'FK tới collections',
    product_id    BIGINT      NOT NULL COMMENT 'FK tới products',
    position      INT         NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị sản phẩm',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (collection_id, product_id),
    KEY idx_col_prod_product (product_id),
    CONSTRAINT fk_col_prod_col FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE,
    CONSTRAINT fk_col_prod_prod FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sản phẩm thuộc bộ sưu tập thủ công';
