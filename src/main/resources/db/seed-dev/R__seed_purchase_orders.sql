-- =====================================================================
-- R__seed_purchase_orders.sql : Repeatable seed data cho Nhà cung cấp
-- Chỉ nạp trên profile dev (spring.flyway.locations=classpath:db/migration,classpath:db/seed-dev)
-- =====================================================================

INSERT INTO suppliers (name, email, phone, address, contact_name, created_at, updated_at)
SELECT 'Công ty Cổ phần May mặc Hà Nội', 'contact@hanoigarment.vn', '02438889999', 'KCN Đài Tư, Long Biên, Hà Nội', 'Nguyễn Văn Hùng', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM suppliers WHERE email = 'contact@hanoigarment.vn'
);
