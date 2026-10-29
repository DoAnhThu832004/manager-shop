-- =====================================================================
-- R__seed_products.sql : Dữ liệu mẫu cho Module Sản phẩm (chỉ dùng profile dev)
-- Sản phẩm 'Áo thun' ACTIVE, Option Size (S, M, L) và Color (Đen, Trắng), 6 biến thể có SKU và giá
-- Idempotent: có thể chạy nhiều lần mà không tạo trùng lặp
-- =====================================================================

-- 1. Sản phẩm 'Áo thun'
INSERT INTO products (id, title, description, status, category_id, category_name, product_type, vendor, tags, theme_template, created_at, updated_at)
VALUES (101, 'Áo thun', '<p>Áo thun cotton cao cấp thoáng mát, thấm hút mồ hôi</p>', 'ACTIVE', 'CAT-01', 'Thời trang nam', 'Áo thun', 'Shopify Store', '["thời trang", "áo thun", "nam", "cotton"]', 'Default product', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    title = VALUES(title),
    description = VALUES(description),
    status = VALUES(status),
    category_id = VALUES(category_id),
    category_name = VALUES(category_name),
    product_type = VALUES(product_type),
    vendor = VALUES(vendor),
    tags = VALUES(tags),
    updated_at = CURRENT_TIMESTAMP(3);

-- 2. Options: Size và Color
INSERT INTO product_options (id, product_id, name, position, created_at, updated_at)
VALUES 
(201, 101, 'Size', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(202, 101, 'Color', 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    name = VALUES(name),
    position = VALUES(position),
    updated_at = CURRENT_TIMESTAMP(3);

-- 3. Option Values: S, M, L cho Size; Đen, Trắng cho Color
INSERT INTO product_option_values (id, option_id, value, position, created_at, updated_at)
VALUES 
(301, 201, 'S', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(302, 201, 'M', 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(303, 201, 'L', 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(304, 202, 'Đen', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(305, 202, 'Trắng', 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    value = VALUES(value),
    position = VALUES(position),
    updated_at = CURRENT_TIMESTAMP(3);

-- 4. 6 Biến thể với tổ hợp (Size x Color)
INSERT INTO product_variants (id, product_id, title, option1, option2, option3, price, compare_at_price, cost_per_item, charge_tax, sku, barcode, inventory_tracked, sell_when_out_of_stock, is_physical, weight_kg, position, created_at, updated_at)
VALUES 
(401, 101, 'S / Đen', 'S', 'Đen', NULL, 150000.00, 180000.00, 90000.00, TRUE, 'AT-S-DEN', '8930000001', TRUE, FALSE, TRUE, 0.200, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(402, 101, 'S / Trắng', 'S', 'Trắng', NULL, 150000.00, 180000.00, 90000.00, TRUE, 'AT-S-TRANG', '8930000002', TRUE, FALSE, TRUE, 0.200, 2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(403, 101, 'M / Đen', 'M', 'Đen', NULL, 150000.00, 180000.00, 90000.00, TRUE, 'AT-M-DEN', '8930000003', TRUE, FALSE, TRUE, 0.200, 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(404, 101, 'M / Trắng', 'M', 'Trắng', NULL, 150000.00, 180000.00, 90000.00, TRUE, 'AT-M-TRANG', '8930000004', TRUE, FALSE, TRUE, 0.200, 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(405, 101, 'L / Đen', 'L', 'Đen', NULL, 160000.00, 190000.00, 95000.00, TRUE, 'AT-L-DEN', '8930000005', TRUE, FALSE, TRUE, 0.220, 5, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(406, 101, 'L / Trắng', 'L', 'Trắng', NULL, 160000.00, 190000.00, 95000.00, TRUE, 'AT-L-TRANG', '8930000006', TRUE, FALSE, TRUE, 0.220, 6, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    title = VALUES(title),
    price = VALUES(price),
    compare_at_price = VALUES(compare_at_price),
    cost_per_item = VALUES(cost_per_item),
    sku = VALUES(sku),
    barcode = VALUES(barcode),
    position = VALUES(position),
    updated_at = CURRENT_TIMESTAMP(3);
