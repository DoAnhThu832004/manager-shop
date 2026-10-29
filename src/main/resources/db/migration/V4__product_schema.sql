-- =====================================================================
-- V4__product_schema.sql : Module Sản phẩm (Products, Options, Variants, Media)
-- Quy ước: thời gian lưu UTC (DATETIME(3)), id BIGINT AUTO_INCREMENT.
-- =====================================================================

-- 1. Bảng products
CREATE TABLE products (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính sản phẩm',
    title          VARCHAR(255) NOT NULL COMMENT 'Tiêu đề sản phẩm',
    description    LONGTEXT     NULL COMMENT 'Mô tả chi tiết (HTML)',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'ACTIVE, DRAFT, ARCHIVED',
    category_id    VARCHAR(100) NULL COMMENT 'ID danh mục',
    category_name  VARCHAR(255) NULL COMMENT 'Tên danh mục',
    product_type   VARCHAR(255) NULL COMMENT 'Loại sản phẩm',
    vendor         VARCHAR(255) NULL COMMENT 'Nhà cung cấp / thương hiệu',
    tags           JSON         NULL COMMENT 'Mảng chuỗi tags',
    theme_template VARCHAR(100) NOT NULL DEFAULT 'Default product' COMMENT 'Giao diện mẫu',
    deleted_at     DATETIME(3)  NULL COMMENT 'Xóa mềm',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_products_status (status),
    KEY idx_products_vendor (vendor),
    KEY idx_products_product_type (product_type),
    KEY idx_products_created_at (created_at),
    KEY idx_products_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sản phẩm';

-- 2. Bảng product_options
CREATE TABLE product_options (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính thuộc tính',
    product_id BIGINT       NOT NULL COMMENT 'FK tới products',
    name       VARCHAR(100) NOT NULL COMMENT 'Tên thuộc tính (Size, Color...)',
    position   INT          NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_options_name (product_id, name),
    KEY idx_product_options_product (product_id),
    CONSTRAINT fk_product_options_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Thuộc tính sản phẩm (tối đa 3)';

-- 3. Bảng product_option_values
CREATE TABLE product_option_values (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính giá trị thuộc tính',
    option_id  BIGINT       NOT NULL COMMENT 'FK tới product_options',
    value      VARCHAR(100) NOT NULL COMMENT 'Giá trị thuộc tính (S, M, Đỏ...)',
    position   INT          NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_option_values_val (option_id, value),
    KEY idx_option_values_option (option_id),
    CONSTRAINT fk_option_values_option FOREIGN KEY (option_id) REFERENCES product_options (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Giá trị của thuộc tính';

-- 4. Bảng product_variants
CREATE TABLE product_variants (
    id                     BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính biến thể',
    product_id             BIGINT         NOT NULL COMMENT 'FK tới products',
    title                  VARCHAR(255)   NOT NULL COMMENT 'Tên biến thể (M / Đỏ hoặc Default Title)',
    option1                VARCHAR(100)   NULL COMMENT 'Giá trị option 1',
    option2                VARCHAR(100)   NULL COMMENT 'Giá trị option 2',
    option3                VARCHAR(100)   NULL COMMENT 'Giá trị option 3',
    price                  DECIMAL(19,2)  NOT NULL DEFAULT 0.00 COMMENT 'Giá bán',
    compare_at_price       DECIMAL(19,2)  NULL COMMENT 'Giá so sánh',
    cost_per_item          DECIMAL(19,2)  NULL COMMENT 'Giá vốn',
    charge_tax             BOOLEAN        NOT NULL DEFAULT TRUE COMMENT 'Áp dụng thuế',
    unit_price_measure     VARCHAR(50)    NULL COMMENT 'Đơn vị tính',
    sku                    VARCHAR(100)   NULL COMMENT 'Mã SKU',
    barcode                VARCHAR(100)   NULL COMMENT 'Mã vạch barcode',
    inventory_tracked      BOOLEAN        NOT NULL DEFAULT TRUE COMMENT 'Có theo dõi tồn kho không',
    sell_when_out_of_stock BOOLEAN        NOT NULL DEFAULT FALSE COMMENT 'Tiếp tục bán khi hết hàng',
    is_physical            BOOLEAN        NOT NULL DEFAULT TRUE COMMENT 'Sản phẩm vật lý cần giao nhận',
    weight_kg              DECIMAL(10,3)  NULL COMMENT 'Khối lượng (kg)',
    length_cm              DECIMAL(10,2)  NULL COMMENT 'Chiều dài (cm)',
    width_cm               DECIMAL(10,2)  NULL COMMENT 'Chiều rộng (cm)',
    height_cm              DECIMAL(10,2)  NULL COMMENT 'Chiều cao (cm)',
    country_of_origin      VARCHAR(10)    NULL COMMENT 'Quốc gia xuất xứ ISO',
    hs_code                VARCHAR(50)    NULL COMMENT 'Mã HS',
    position               INT            NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị',
    deleted_at             DATETIME(3)    NULL COMMENT 'Xóa mềm',
    created_at             DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    -- Cột sinh phục vụ UNIQUE cho SKU khi chưa bị xóa mềm
    active_sku             VARCHAR(100)   GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN sku ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_variants_active_sku (active_sku),
    KEY idx_variants_product (product_id),
    KEY idx_variants_sku (sku),
    KEY idx_variants_barcode (barcode),
    KEY idx_variants_deleted_at (deleted_at),
    CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_variants_price CHECK (price >= 0),
    CONSTRAINT chk_variants_compare_price CHECK (compare_at_price IS NULL OR compare_at_price > price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Biến thể sản phẩm';

-- 5. Bảng product_media
CREATE TABLE product_media (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính media',
    product_id BIGINT        NOT NULL COMMENT 'FK tới products',
    url        VARCHAR(1000) NOT NULL COMMENT 'Đường dẫn ảnh/video/model',
    media_type VARCHAR(20)   NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE, VIDEO, MODEL_3D',
    alt_text   VARCHAR(255)  NULL COMMENT 'Mô tả hình ảnh',
    position   INT           NOT NULL DEFAULT 1 COMMENT 'Thứ tự hiển thị',
    created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_media_product_position (product_id, position),
    CONSTRAINT fk_media_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tệp đa phương tiện của sản phẩm';
