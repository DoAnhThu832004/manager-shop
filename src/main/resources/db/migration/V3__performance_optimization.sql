-- =====================================================================
-- V3__performance_optimization.sql : Chỉ mục tối ưu hóa hiệu năng cho bảng log
-- =====================================================================

CREATE INDEX idx_la_success_created ON login_attempts (success, created_at DESC);
