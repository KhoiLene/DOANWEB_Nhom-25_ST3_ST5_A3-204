param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath
)

$ErrorActionPreference = 'Stop'
$BackupPath = (Resolve-Path -LiteralPath $BackupPath).Path
$containerName = 'shop-chain-db'

Write-Warning 'Thao tác này xóa và thay thế database shop_chain_db trên máy này.'
$confirmation = Read-Host 'Nhập RESTORE để tiếp tục'
if ($confirmation -cne 'RESTORE') {
    Write-Output 'Đã hủy, database chưa bị thay đổi.'
    exit 0
}

docker compose up -d postgres
if ($LASTEXITCODE -ne 0) { throw 'Không khởi động được PostgreSQL bằng Docker Compose.' }

$ready = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    docker exec $containerName pg_isready -U postgres -d postgres | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw 'PostgreSQL chưa sẵn sàng sau 60 giây.' }

docker exec $containerName dropdb --if-exists --force -U postgres shop_chain_db
if ($LASTEXITCODE -ne 0) { throw 'Không xóa được database cũ.' }
docker exec $containerName createdb -U postgres shop_chain_db
if ($LASTEXITCODE -ne 0) { throw 'Không tạo được database đích.' }

$containerDumpPath = '/tmp/shop_chain_db_restore.sql'
docker cp $BackupPath "${containerName}:$containerDumpPath"
if ($LASTEXITCODE -ne 0) { throw 'Không chép được file dump vào container PostgreSQL.' }

try {
    docker exec $containerName psql -v ON_ERROR_STOP=1 -U postgres -d shop_chain_db -f $containerDumpPath
    if ($LASTEXITCODE -ne 0) { throw 'Khôi phục thất bại. Kiểm tra file dump và log PostgreSQL.' }
} finally {
    docker exec $containerName rm -f $containerDumpPath | Out-Null
}

Write-Output 'Đã khôi phục database. Có thể khởi động ứng dụng.'
