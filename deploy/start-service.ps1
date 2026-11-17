# 精读圣经 · 单服务启动脚本
# 通过 -Name 指定要启动的软件，已运行则自动跳过。
# 用法：
#   powershell -ExecutionPolicy Bypass -File deploy\start-service.ps1 -Name kibana
#   powershell -ExecutionPolicy Bypass -File deploy\start-service.ps1 -Name backend -Build
#   powershell -ExecutionPolicy Bypass -File deploy\start-service.ps1 -List
param(
    [Parameter(Position = 0)]
    [ValidateSet('kibana', 'backend', 'elasticsearch', 'redis', 'nginx', 'mysql', 'all')]
    [string]$Name = 'kibana',
    [switch]$Build,
    [switch]$List
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Runtime = Join-Path $Root '.runtime'
$Pids = Join-Path $Runtime 'pids'
$Logs = Join-Path $Runtime 'logs'
New-Item -ItemType Directory -Force -Path $Runtime, $Pids, $Logs | Out-Null

$JavaHome = 'D:\dev\Java\jdk-17'
$Maven = 'D:\dev\maven\apache-maven-3.9.9\bin\mvn.cmd'
$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
$RedisHome = 'D:\dev\Redis-x64-3.2.100'
$ElasticHome = 'D:\dev\elasticsearch'
$NginxHome = 'D:\dev\nginx-1.20.2'
$KibanaHome = 'D:\dev\kibana-7.16.2-windows-x86_64'

function Show-Services {
    Write-Host '支持的服务：' -ForegroundColor Cyan
    Write-Host '  kibana         5601  Kibana 日志可视化'
    Write-Host '  backend        8080  Spring Boot（-Build 可先构建）'
    Write-Host '  elasticsearch  9200  Elasticsearch'
    Write-Host '  redis          6379  Redis'
    Write-Host '  nginx          8088  前端静态站点 + API 代理'
    Write-Host '  mysql          3307  MySQL 回退实例（3306 可用时跳过）'
    Write-Host '  all                 等同 deploy\start-all.ps1'
}

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Port([int]$Port, [int]$Seconds, [string]$Label) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-Port $Port) { Write-Host "  [OK] $Label :$Port" -ForegroundColor Green; return $true }
        Start-Sleep -Seconds 1
    }
    Write-Warning "  [WARN] $Label 未在 $Seconds 秒内监听 :$Port（日志：$Logs）"
    return $false
}

function Wait-Http([string]$Url, [int]$Seconds, [string]$Label) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($r.StatusCode -eq 200) {
                Write-Host "  [OK] $Label 响应正常：$Url" -ForegroundColor Green
                return $true
            }
        } catch { }
        Start-Sleep -Seconds 1
    }
    Write-Warning "  [WARN] $Label 未在 $Seconds 秒内就绪（日志：$Logs）"
    return $false
}

function Start-Background([string]$Service, [string]$File, [string[]]$Arguments, [string]$WorkDir) {
    $out = Join-Path $Logs "$Service.out.log"
    $err = Join-Path $Logs "$Service.err.log"

    # 通过 cmd 统一处理参数、输出重定向（每次启动覆盖，避免日志无限增长）
    $argPart = ''
    if ($Arguments) {
        $escaped = $Arguments | ForEach-Object { if ($_ -match '\s') { "`"$_`"" } else { $_ } }
        $argPart = ' ' + ($escaped -join ' ')
    }
    $inner = "`"$File`"$argPart"
    $commandLine = "cmd.exe /c `"$inner > `"$out`" 2> `"$err`"`""

    # 用 WMI 创建进程，脚本退出或终端关闭后服务仍继续运行
    $result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine      = $commandLine
        CurrentDirectory = $WorkDir
    }
    if ($result.ReturnValue -ne 0) {
        throw "启动 $Service 失败（Win32_Process.Create 返回 $($result.ReturnValue)）"
    }

    Set-Content -Path (Join-Path $Pids "$Service.pid") -Value $result.ProcessId
    Write-Host "  [START] $Service (PID $($result.ProcessId)，日志 $out)" -ForegroundColor Cyan
}

if ($List) {
    Show-Services
    exit 0
}

Write-Host "== 精读圣经 · 启动 $Name ==" -ForegroundColor Cyan

if ($Name -eq 'all') {
    $startAll = Join-Path $PSScriptRoot 'start-all.ps1'
    $allArgs = @()
    if (-not $Build) { $allArgs += '-SkipBuild' }
    & powershell -ExecutionPolicy Bypass -File $startAll @allArgs
    exit $LASTEXITCODE
}

switch ($Name) {
    'kibana' {
        if (Test-Port 5601) {
            Write-Host '  [SKIP] Kibana 5601 已在运行'
            exit 0
        }
        if (-not (Test-Path (Join-Path $KibanaHome 'bin\kibana.bat'))) {
            throw "未找到 Kibana：$KibanaHome"
        }
        Start-Background 'kibana' (Join-Path $KibanaHome 'bin\kibana.bat') @() $KibanaHome | Out-Null
        Write-Host '      等待 Kibana 就绪（首次约 20-40 秒）...'
        Wait-Http 'http://127.0.0.1:5601/api/status' 120 'Kibana' | Out-Null
        Write-Host '  访问地址 http://127.0.0.1:5601/app/discover'
    }

    'backend' {
        if (Test-Port 8080) {
            Write-Host '  [SKIP] 后端 8080 已在运行'
            exit 0
        }
        $jar = Join-Path $Root 'backend\target\bible-reader.jar'
        if ($Build -or -not (Test-Path $jar)) {
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
        Start-Background 'backend' (Join-Path $JavaHome 'bin\java.exe') @('-jar', $jar) (Join-Path $Root 'backend') | Out-Null
        Wait-Http 'http://127.0.0.1:8080/api/health' 60 '后端' | Out-Null
    }

    'elasticsearch' {
        if (Test-Port 9200) {
            Write-Host '  [SKIP] Elasticsearch 9200 已在运行'
            exit 0
        }
        $env:ES_JAVA_OPTS = '-Xms512m -Xmx512m'
        Start-Background 'elasticsearch' (Join-Path $ElasticHome 'bin\elasticsearch.bat') @() $ElasticHome | Out-Null
        Write-Host '      等待 Elasticsearch 就绪（首次约 20-40 秒）...'
        Wait-Http 'http://127.0.0.1:9200/' 90 'Elasticsearch' | Out-Null
    }

    'redis' {
        if (Test-Port 6379) {
            Write-Host '  [SKIP] Redis 6379 已在运行'
            exit 0
        }
        Start-Background 'redis' (Join-Path $RedisHome 'redis-server.exe') `
            @((Join-Path $RedisHome 'redis.windows.conf')) $RedisHome | Out-Null
        Wait-Port 6379 15 'Redis' | Out-Null
    }

    'nginx' {
        if (Test-Port 8088) {
            Write-Host '  [SKIP] Nginx 8088 已在运行'
            exit 0
        }
        Start-Background 'nginx' (Join-Path $NginxHome 'nginx.exe') `
            @('-p', $NginxHome, '-c', (Join-Path $Root 'deploy\nginx.conf')) $NginxHome | Out-Null
        Wait-Port 8088 10 'Nginx' | Out-Null
        Write-Host '  访问地址 http://127.0.0.1:8088/'
    }

    'mysql' {
        if (Test-Port 3306) {
            Write-Host '  [SKIP] MySQL 3306 已在运行（主库）'
            exit 0
        }
        if (Test-Port 3307) {
            Write-Host '  [SKIP] MySQL 3307 回退实例已在运行'
            exit 0
        }
        Start-Background 'mysql' (Join-Path $MySqlHome 'bin\mysqld.exe') `
            @("--defaults-file=$Runtime\mysql\my.ini", '--console') $MySqlHome | Out-Null
        Wait-Port 3307 40 'MySQL 3307 回退' | Out-Null
    }
}

Write-Host '完成。' -ForegroundColor Green
