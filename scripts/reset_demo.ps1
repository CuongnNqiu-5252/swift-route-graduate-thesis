# scripts/reset_demo.ps1
# Công cụ hỗ trợ Reset và tạo dữ liệu đơn hàng phục vụ Demo đồ án SwiftRoute
#
# Cách dùng:
#   .\scripts\reset_demo.ps1 dispatch    -> Xoá hết đơn cũ, nạp 8 đơn PAID chờ điều phối trên Web Admin
#   .\scripts\reset_demo.ps1 driver      -> Xoá hết đơn cũ, nạp đơn phân công cho tài xế để demo Mobile App
#   .\scripts\reset_demo.ps1 clean       -> Xoá sạch toàn bộ đơn hàng

param (
    [ValidateSet("dispatch", "driver", "clean")]
    [string]$Mode = "dispatch"
)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " SwiftRoute Demo Data Manager - Mode: $Mode" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

if ($Mode -eq "clean") {
    docker exec -i swiftroute-postgres psql -U openpg -d postgres -c "DELETE FROM orders;"
    Write-Host "[OK] Da xoa sach toan bo don hang trong database!" -ForegroundColor Green
    exit 0
}

$sqlFile = if ($Mode -eq "dispatch") { "scripts/demo_orders_for_dispatch.sql" } else { "scripts/demo_orders_for_driver.sql" }

if (!(Test-Path $sqlFile)) {
    Write-Host "[ERROR] Khong tim thay file $sqlFile" -ForegroundColor Red
    exit 1
}

# Sao chép file SQL vào Docker container postgres
docker cp $sqlFile swiftroute-postgres:/tmp/target_seed.sql

# Thực thi lệnh trong postgres
docker exec -i swiftroute-postgres psql -U openpg -d postgres -f /tmp/target_seed.sql

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
if ($Mode -eq "dispatch") {
    Write-Host " [THANH CONG] Da nap 8 don hang moi o trang thai PAID!" -ForegroundColor Green
    Write-Host " -> Mo Web Admin http://localhost:3000/dispatch de chon don va chay Toi uu tuyen duong (AI/DP)." -ForegroundColor Yellow
} else {
    Write-Host " [THANH CONG] Da nap don hang cho Tai xe 1 (driver@gmail.com)!" -ForegroundColor Green
    Write-Host " -> Gom 4 don da DELIVERED (tinh thu lao ca) va 2 don PLANNED de test Bao su co / Giao hang tren Mobile." -ForegroundColor Yellow
}
Write-Host "==================================================" -ForegroundColor Green

