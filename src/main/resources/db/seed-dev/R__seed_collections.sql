-- =====================================================================
-- R__seed_collections.sql : Dữ liệu mẫu cho Module Bộ sưu tập (chỉ dùng profile dev)
-- Bộ sưu tập MANUAL 'Home page' chứa sản phẩm 'Áo thun' (id=101)
-- Idempotent: có thể chạy nhiều lần mà không tạo trùng lặp
-- =====================================================================

-- 1. Bộ sưu tập 'Home page'
INSERT INTO collections (id, title, description, collection_type, rule_applies_disjunctive, created_at, updated_at)
VALUES (101, 'Home page', '<p>Bộ sưu tập các sản phẩm hiển thị trên trang chủ</p>', 'MANUAL', FALSE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    title = VALUES(title),
    description = VALUES(description),
    collection_type = VALUES(collection_type),
    updated_at = CURRENT_TIMESTAMP(3);

-- 2. Gán sản phẩm 'Áo thun' (id=101) vào bộ sưu tập 'Home page' (id=101)
INSERT INTO collection_products (collection_id, product_id, position, created_at)
SELECT 101, 101, 1, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM collection_products WHERE collection_id = 101 AND product_id = 101
);
