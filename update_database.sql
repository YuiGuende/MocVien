-- Script để cập nhật database sau khi thêm trường cost vào Product
-- Chạy script này để cập nhật các bản ghi cũ

-- BƯỚC 1: Thêm cột cost vào bảng products (nếu chưa có)
-- SQLite không hỗ trợ IF NOT EXISTS cho ALTER TABLE, nên sẽ bỏ qua lỗi nếu cột đã tồn tại
ALTER TABLE products ADD COLUMN cost REAL DEFAULT 0.0;

-- BƯỚC 2: Cập nhật tất cả products có cost là NULL thành 0.0
UPDATE products SET cost = 0.0 WHERE cost IS NULL;

-- Hoặc nếu muốn set cost = 50% của price (ví dụ)
-- UPDATE products SET cost = price * 0.5 WHERE cost IS NULL OR cost = 0.0;

-- Xem kết quả
SELECT id, name, price, cost FROM products;

