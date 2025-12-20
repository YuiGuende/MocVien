# Script PowerShell để cập nhật database
# Chạy script này trong PowerShell: .\update_database.ps1

Write-Host "=== Cập nhật Database SQLite ===" -ForegroundColor Green
Write-Host ""

# Kiểm tra file database
$dbFile = "cafe.db"
if (-not (Test-Path $dbFile)) {
    Write-Host "Lỗi: Không tìm thấy file $dbFile" -ForegroundColor Red
    Write-Host "Hãy đảm bảo bạn đang ở thư mục gốc của dự án" -ForegroundColor Yellow
    exit 1
}

# Backup database
$backupFile = "cafe.db.backup"
if (Test-Path $backupFile) {
    Remove-Item $backupFile -Force
}
Copy-Item $dbFile $backupFile
Write-Host "✓ Đã backup database thành $backupFile" -ForegroundColor Green

# Kiểm tra SQLite có sẵn không
$sqlitePath = $null

# Thử tìm SQLite trong PATH
$sqliteCommands = @("sqlite3", "sqlite")
foreach ($cmd in $sqliteCommands) {
    $found = Get-Command $cmd -ErrorAction SilentlyContinue
    if ($found) {
        $sqlitePath = $cmd
        break
    }
}

# Nếu không tìm thấy, thử tìm trong thư mục Git (nếu có)
if (-not $sqlitePath) {
    $gitPath = "C:\Program Files\Git\usr\bin\sqlite3.exe"
    if (Test-Path $gitPath) {
        $sqlitePath = $gitPath
    }
}

if (-not $sqlitePath) {
    Write-Host ""
    Write-Host "⚠ Không tìm thấy SQLite command line tool" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Có 2 cách:" -ForegroundColor Cyan
    Write-Host "1. Tải SQLite từ: https://www.sqlite.org/download.html" -ForegroundColor White
    Write-Host "2. Hoặc dùng DB Browser for SQLite: https://sqlitebrowser.org/dl/" -ForegroundColor White
    Write-Host ""
    Write-Host "Sau đó mở file cafe.db và chạy lệnh SQL:" -ForegroundColor Yellow
    Write-Host "   UPDATE products SET cost = 0.0 WHERE cost IS NULL;" -ForegroundColor White
    exit 1
}

Write-Host "✓ Tìm thấy SQLite: $sqlitePath" -ForegroundColor Green
Write-Host ""

# Tạo file SQL tạm
$tempSql = "temp_update.sql"
@"
-- BƯỚC 1: Thêm cột cost vào bảng products (nếu chưa có)
-- Lưu ý: SQLite sẽ báo lỗi nếu cột đã tồn tại, nhưng không sao
ALTER TABLE products ADD COLUMN cost REAL DEFAULT 0.0;

-- BƯỚC 2: Cập nhật tất cả products có cost là NULL thành 0.0
UPDATE products SET cost = 0.0 WHERE cost IS NULL;

-- Kiểm tra kết quả
SELECT 'Total products: ' || COUNT(*) as result FROM products;
SELECT 'Products with cost = 0.0: ' || COUNT(*) as result FROM products WHERE cost = 0.0;
"@ | Out-File -FilePath $tempSql -Encoding UTF8

Write-Host "Đang cập nhật database..." -ForegroundColor Yellow

# Chạy SQL
Write-Host "Đang thêm cột cost và cập nhật dữ liệu..." -ForegroundColor Yellow
Write-Host ""

try {
    # Tạo file SQL với xử lý lỗi
    $sqlContent = @"
-- Thêm cột cost (bỏ qua lỗi nếu đã tồn tại)
-- SQLite không có IF NOT EXISTS cho ALTER TABLE, nên sẽ thử thêm và bỏ qua lỗi
BEGIN TRANSACTION;
-- Thử thêm cột
ALTER TABLE products ADD COLUMN cost REAL DEFAULT 0.0;
COMMIT;
"@
    
    # Tạo file SQL tạm cho ALTER TABLE
    $alterSql = "temp_alter.sql"
    $sqlContent | Out-File -FilePath $alterSql -Encoding UTF8
    
    # Chạy ALTER TABLE (có thể lỗi nếu cột đã tồn tại, nhưng không sao)
    Write-Host "  Đang thêm cột cost..." -ForegroundColor Yellow
    $alterOutput = & $sqlitePath $dbFile ".read $alterSql" 2>&1
    if ($alterOutput -like "*duplicate column name*" -or $alterOutput -like "*already exists*") {
        Write-Host "  ⚠ Cột cost đã tồn tại, tiếp tục..." -ForegroundColor Yellow
    } elseif ($LASTEXITCODE -ne 0 -and $alterOutput) {
        Write-Host "  ⚠ Cảnh báo: $alterOutput" -ForegroundColor Yellow
    } else {
        Write-Host "  ✓ Đã thêm cột cost" -ForegroundColor Green
    }
    
    # Xóa file tạm
    if (Test-Path $alterSql) {
        Remove-Item $alterSql -Force
    }
    
    # Cập nhật dữ liệu
    Write-Host "  Đang cập nhật giá trị cost..." -ForegroundColor Yellow
    $updateOutput = & $sqlitePath $dbFile "UPDATE products SET cost = 0.0 WHERE cost IS NULL;" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw $updateOutput
    }
    Write-Host "  ✓ Đã cập nhật dữ liệu" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "✓ Cập nhật thành công!" -ForegroundColor Green
    Write-Host ""
    
    # Hiển thị kết quả
    Write-Host "Kiểm tra kết quả:" -ForegroundColor Cyan
    & $sqlitePath $dbFile "SELECT COUNT(*) as 'Total products' FROM products;"
    & $sqlitePath $dbFile "SELECT COUNT(*) as 'Products with cost' FROM products WHERE cost IS NOT NULL;"
    
} catch {
    Write-Host ""
    Write-Host "✗ Lỗi khi chạy SQL: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Hãy mở file cafe.db bằng DB Browser for SQLite và chạy thủ công:" -ForegroundColor Yellow
    Write-Host "   ALTER TABLE products ADD COLUMN cost REAL DEFAULT 0.0;" -ForegroundColor White
    Write-Host "   UPDATE products SET cost = 0.0 WHERE cost IS NULL;" -ForegroundColor White
} finally {
    # Xóa file SQL tạm
    if (Test-Path $tempSql) {
        Remove-Item $tempSql -Force
    }
}

Write-Host ""
Write-Host "=== Hoàn tất ===" -ForegroundColor Green
Write-Host "Hãy khởi động lại ứng dụng Spring Boot" -ForegroundColor Cyan

