# 精读圣经 · 一键启动脚本
# 依次保证 MySQL(3307) / Redis(6379) / Elasticsearch(9200) / Spring Boot(8080) / Nginx(8088)
# 用法：powershell -ExecutionPolicy Bypass -File deploy\start-all.ps1
param(
    [switch]$SkipBuild,
    [switch]$NoFrontend
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Runtime = Join-Path $Root '.runtime'
$Pids = Join-Path $Runtime 'pids'
$Logs = Join-Path $Runtime 'logs'
New-Item -ItemType Directory -Force -Path $Runtime, $Pids, $Logs, (Join-Path $Runtime 'mysql\log'), (Join-Path $Runtime 'nginx') | Out-Null

$JavaHome = 'D:\dev\Java\jdk-17'
$Maven = 'D:\dev\maven\apache-maven-3.9.9\bin\mvn.cmd'
$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
$RedisHome = 'D:\dev\Redis-x64-3.2.100'
$ElasticHome = 'D:\dev\elasticsearch'
$NginxHome = 'D:\dev\nginx-1.20.2'

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Port([int]$Port, [int]$Seconds, [string]$Name) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-Port $Port) { Write-Host "  [OK] $Name :$Port"; return $true }
        Start-Sleep -Seconds 1
    }
    Write-Warning "  [WARN] $Name 未在 $Seconds 秒内监听 :$Port"
    return $false
}

function Start-Background([string]$Name, [string]$File, [string[]]$Arguments, [string]$WorkDir) {
    $out = Join-Path $Logs "$Name.out.log"
    $err = Join-Path $Logs "$Name.err.log"

    $filePath = $File
    $argList = @()
    if ($Arguments) { $argList += $Arguments }
    # .bat 需要经 cmd.exe 启动；Start-Process 不能对空 ArgumentList 做参数校验
    if ($File -match '\.(bat|cmd)$') {
        $filePath = $env:ComSpec
        $argList = @('/c', "`"$File`"") + $argList
    }

    $params = @{
        FilePath               = $filePath
        WorkingDirectory       = $WorkDir
        WindowStyle            = 'Hidden'
        RedirectStandardOutput = $out
        RedirectStandardError  = $err
        PassThru               = $true
    }
    if ($argList.Count -gt 0) { $params['ArgumentList'] = $argList }

    $process = Start-Process @params
    Set-Content -Path (Join-Path $Pids "$Name.pid") -Value $process.Id
    Write-Host "  [START] $Name (PID $($process.Id))"
    return $process
}

Write-Host '== 精读圣经 启动 ==' -ForegroundColor Cyan

# ---------- MySQL ----------
if (Test-Port 3306) {
    Write-Host '[1/5] MySQL 3306 已在运行（主库 root/1106135）'
} elseif (-not (Test-Port 3307)) {
    Write-Host '[1/5] MySQL 3306 未运行，启动 3307 回退实例 ...'
    Start-Background 'mysql' (Join-Path $MySqlHome 'bin\mysqld.exe') `
        @("--defaults-file=$Runtime\mysql\my.ini", '--console') $MySqlHome | Out-Null
    Wait-Port 3307 40 'MySQL 3307 回退' | Out-Null
} else {
    Write-Host '[1/5] MySQL 3306 未运行，3307 回退实例已在运行'
}

# ---------- Redis ----------
if (-not (Test-Port 6379)) {
    Write-Host '[2/5] 启动 Redis 6379 ...'
    Start-Background 'redis' (Join-Path $RedisHome 'redis-server.exe') `
        @((Join-Path $RedisHome 'redis.windows.conf')) $RedisHome | Out-Null
    Wait-Port 6379 15 'Redis' | Out-Null
} else {
    Write-Host '[2/5] Redis 6379 已在运行'
}

# ---------- Elasticsearch ----------
if (-not (Test-Port 9200)) {
    Write-Host '[3/5] 启动 Elasticsearch 9200（首次启动约需 20-40 秒）...'
    $env:ES_JAVA_OPTS = '-Xms512m -Xmx512m'
    Start-Background 'elasticsearch' (Join-Path $ElasticHome 'bin\elasticsearch.bat') @() $ElasticHome | Out-Null
    $ok = $false
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $null = Invoke-RestMethod -Uri 'http://127.0.0.1:9200/' -TimeoutSec 2
            $ok = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if ($ok) { Write-Host '  [OK] Elasticsearch :9200' } else { Write-Warning '  [WARN] Elasticsearch 启动超时（日志：.runtime\logs\elasticsearch.*.log）' }
} else {
    Write-Host '[3/5] Elasticsearch 9200 已在运行'
}

# ---------- Backend ----------
Write-Host '[4/5] 后端 ...'
$Jar = Join-Path $Root 'backend\target\bible-reader.jar'
if (-not $SkipBuild -or -not (Test-Path $Jar)) {
    Write-Host '      构建后端 ...'
    $env:JAVA_HOME = $JavaHome
    Push-Location (Join-Path $Root 'backend')
    try {
        & $Maven -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败' }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Port 8080)) {
    Write-Host '      启动 Spring Boot 8080 ...'
    if (-not (Test-Path $Jar)) { throw "找不到后端产物：$Jar" }
    Start-Background 'backend' (Join-Path $JavaHome 'bin\java.exe') @('-jar', $Jar) (Join-Path $Root 'backend') | Out-Null
    Wait-Port 8080 60 'Spring Boot' | Out-Null
} else {
    Write-Host '      Spring Boot 8080 已在运行'
}

# ---------- Frontend ----------
if (-not $NoFrontend) {
    $Frontend = Join-Path $Root 'frontend'
    Write-Host '[5/5] 构建前端 ...'
    Push-Location $Frontend
    try {
        if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
            & 'D:\dev\node\npm.cmd' install --no-audit --no-fund
        }
        & 'D:\dev\node\npm.cmd' run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    } finally {
        Pop-Location
    }

    if (-not (Test-Port 8088)) {
        Write-Host '      启动 Nginx 8088 ...'
        $NginxExe = Join-Path $NginxHome 'nginx.exe'
        Start-Process -FilePath $NginxExe `
            -ArgumentList @('-p', $NginxHome, '-c', (Join-Path $Root 'deploy\nginx.conf')) `
            -WorkingDirectory $NginxHome -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $Logs 'nginx.out.log') `
            -RedirectStandardError (Join-Path $Logs 'nginx.err.log') | Out-Null
        Wait-Port 8088 10 'Nginx' | Out-Null
    } else {
        Write-Host '      Nginx 8088 已在运行'
    }
}

Write-Host ''
Write-Host '================ 启动完成 ================' -ForegroundColor Green
Write-Host '  阅读页面   http://127.0.0.1:8088/'
Write-Host '  后端 API   http://127.0.0.1:8080/api/books'
Write-Host '  日志检索   http://127.0.0.1:8088/#/logs'
Write-Host '  ES 状态    http://127.0.0.1:9200/_cluster/health'
Write-Host '===========================================' -ForegroundColor Green
