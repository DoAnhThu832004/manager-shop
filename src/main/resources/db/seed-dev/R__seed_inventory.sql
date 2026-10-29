-- =====================================================================
-- R__seed_inventory.sql : Dữ liệu mẫu tồn kho ban đầu (chỉ dùng profile dev)
-- Đảm bảo có chi nhánh kho chính (id=1, CN-Q1) và tồn kho cho các biến thể của 'Áo thun' (id: 401..406)
-- =====================================================================

-- 1. Đảm bảo chi nhánh kho chính tồn tại (dùng bảng branches từ V2)
INSERT INTO branches (id, code, name, address, phone, status, created_at, updated_at)
VALUES (1, 'CN-Q1', 'Chi nhánh Quận 1 (Kho chính)', '123 Lê Lợi, P. Bến Nghé, Quận 1, TP.HCM', '02812345678', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. Tồn kho ban đầu cho 6 biến thể 'Áo thun' tại Chi nhánh Quận 1
INSERT INTO inventory_levels (variant_id, location_id, available, on_hand, committed, unavailable, incoming, version, created_at, updated_at)
VALUES 
(401, 1, 50, 50, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(402, 1, 50, 50, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(403, 1, 100, 100, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(404, 1, 100, 100, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(405, 1, 30, 30, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(406, 1, 30, 30, 0, 0, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE 
    available = VALUES(available),
    on_hand = VALUES(on_hand),
    updated_at = CURRENT_TIMESTAMP(3);
