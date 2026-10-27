param(
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$containerName = 'shop-chain-db'
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'

if ([string]::IsNullOrWhiteSpace($Path)) {
    $backupDirectory = Join-Path $PSScriptRoot '..\local-backups'
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $Path = Join-Path $backupDirectory "shop_chain_db_$timestamp.sql"
    $Path = [System.IO.Path]::GetFullPath($Path)
} else {
    $Path = [System.IO.Path]::GetFullPath($Path)
    $backupDirectory = Split-Path -Parent $Path
    if ($backupDirectory) {
        New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    }
}

$containerDumpPath = "/tmp/shop_chain_db_$timestamp.sql"
docker exec $containerName pg_dump -U postgres -d shop_chain_db --no-owner --no-privileges -f $containerDumpPath
if ($LASTEXITCODE -ne 0) { throw 'Không sao lưu được database. Kiểm tra Docker và container PostgreSQL.' }

try {
    docker cp "${containerName}:$containerDumpPath" $Path
    if ($LASTEXITCODE -ne 0) { throw 'Không chép được file dump khỏi container.' }
} finally {
    docker exec $containerName rm -f $containerDumpPath | Out-Null
}

Write-Output "Đã tạo bản sao lưu: $Path"
Write-Warning 'File chứa tài khoản, hash mật khẩu và dữ liệu đơn hàng. Không commit hoặc tải lên repo công khai.'
