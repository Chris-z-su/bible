# 精读圣经 · 停止脚本
# 默认只停止后端与 Nginx；加 -All 时同时停止本项目启动的 MySQL / Redis / ES。
param([switch]$All)

$ErrorActionPreference = 'SilentlyContinue'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Runtime = Join-Path $Root '.runtime'
$Pids = Join-Path $Runtime 'pids'
$NginxHome = 'D:\dev\nginx-1.20.2'

Write-Host '== 精读圣经 停止 ==' -ForegroundColor Cyan

# Nginx
if (Get-NetTCPConnection -LocalPort 8088 -State Listen) {
    & (Join-Path $NginxHome 'nginx.exe') -p $NginxHome -c (Join-Path $Root 'deploy\nginx.conf') -s quit
    Start-Sleep -Seconds 2
    Write-Host '  [STOP] Nginx 8088'
} else {
    Write-Host '  Nginx 8088 未运行'
}

# 后端：只停止命令行包含 bible-reader.jar 的 java 进程，避免误杀
$backend = Get-CimInstance Win32_Process -Filter "name='java.exe'" |
    Where-Object { $_.CommandLine -like '*bible-reader.jar*' }
if ($backend) {
    foreach ($proc in $backend) {
        Stop-Process -Id $proc.ProcessId -Force
        Write-Host "  [STOP] Spring Boot (PID $($proc.ProcessId))"
    }
} else {
    Write-Host '  Spring Boot 未运行'
}

# 可选：停止本项目启动的基础服务
if ($All) {
    $targets = @(
        @{ Port = 9200; Name = 'Elasticsearch'; Match = 'elasticsearch' },
        @{ Port = 6379; Name = 'Redis'; Match = 'redis' },
        @{ Port = 3307; Name = 'MySQL'; Match = 'mysqld' }
    )
    foreach ($target in $targets) {
        $conn = Get-NetTCPConnection -LocalPort $target.Port -State Listen
        if (-not $conn) { continue }
        $proc = Get-Process -Id $conn.OwningProcess
        if ($proc.Path -and $proc.Path.ToLower().Contains($target.Match)) {
            Stop-Process -Id $proc.Id -Force
            Write-Host "  [STOP] $($target.Name) :$($target.Port)"
        } else {
            Write-Warning "  :$($target.Port) 被其他进程占用（$($proc.Path)），已跳过"
        }
    }
}

Write-Host '完成。' -ForegroundColor Green
