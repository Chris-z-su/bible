# 精读圣经 · 后端 API 冒烟测试
# 用法：powershell -ExecutionPolicy Bypass -File scripts\verify.ps1
param(
    [string]$Base = 'http://127.0.0.1:8080/api'
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

function Assert-True([string]$Name, [bool]$Condition, [string]$Detail = '') {
    if ($Condition) {
        $script:pass++
        Write-Host "PASS  $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "FAIL  $Name  $Detail" -ForegroundColor Red
    }
}

function Get-Api([string]$Path) {
    return Invoke-RestMethod -Uri "$Base$Path" -TimeoutSec 20
}

Write-Host "== 精读圣经 API 冒烟测试 ==" -ForegroundColor Cyan

# 1. 书卷
$books = (Get-Api '/books').data
Assert-True '书卷数量 66' ($books.Count -eq 66) "count=$($books.Count)"
Assert-True '首卷创世记 50 章' ($books[0].name -eq '创世记' -and $books[0].chapterCount -eq 50)
Assert-True '旧约 39 / 新约 27' (
    ($books | Where-Object { $_.testament -eq 'OT' }).Count -eq 39 -and
    ($books | Where-Object { $_.testament -eq 'NT' }).Count -eq 27
)

# 2. 章节
$ch = (Get-Api '/chapters/43/3').data
Assert-True '约翰福音 3 章 36 节' ($ch.verses.Count -eq 36) "count=$($ch.verses.Count)"
Assert-True '上一章 43:2 / 下一章 43:4' ($ch.prev.chapter -eq 2 -and $ch.next.chapter -eq 4)
Assert-True '分段标题存在' ($ch.titles.Count -ge 1) "titles=$($ch.titles.Count)"
Assert-True '3:16 文本含“神爱世人”' ($ch.verses[15].text -match '神爱世人')
$ps117 = (Get-Api '/chapters/19/117').data
Assert-True '诗篇 117 章 2 节' ($ps117.verses.Count -eq 2)

# 3. 统计与健康
$stats = (Get-Api '/stats').data
Assert-True '统计 66/1189/31102/2667' (
    $stats.books -eq 66 -and $stats.chapters -eq 1189 -and
    $stats.verses -eq 31102 -and $stats.titles -eq 2667
) "stats=$($stats.books)/$($stats.chapters)/$($stats.verses)/$($stats.titles)"
$health = (Get-Api '/health').data
Assert-True '健康检查 UP + 三项依赖' (
    $health.status -eq 'UP' -and $health.mysql -and $health.redis -and $health.elasticsearch
)

# 4. 搜索与引用
$s1 = (Get-Api "/search?q=$([uri]::EscapeDataString('神爱世人'))").data
Assert-True '关键词搜索命中 43:3:16' (
    $s1.total -ge 1 -and $s1.items[0].bookId -eq 43 -and $s1.items[0].chapter -eq 3 -and
    $s1.items[0].verse -eq 16 -and $s1.items[0].highlights.Count -ge 1
) "total=$($s1.total)"
$s2 = (Get-Api "/search?q=$([uri]::EscapeDataString('约3:16'))").data
Assert-True '引用搜索 约翰福音 3:16' ($s2.reference.display -eq '约翰福音 3:16')
$s3 = (Get-Api "/search?q=$([uri]::EscapeDataString('诗23篇'))").data
Assert-True '引用搜索 诗篇 23（6 节）' ($s3.reference.chapter -eq 23 -and $s3.items.Count -eq 6)
$scoped = (Get-Api "/search?q=$([uri]::EscapeDataString('神'))&bookId=43").data
$badScope = @($scoped.items | Where-Object { $_.bookId -ne 43 }).Count
Assert-True '限定书卷搜索仅返回该卷' ($badScope -eq 0) "bad=$badScope"

# 5. 引用解析
$ref = (Get-Api "/reference/parse?q=$([uri]::EscapeDataString('John 3:16'))").data
Assert-True '解析 John 3:16' ($ref.bookId -eq 43 -and $ref.chapter -eq 3 -and $ref.verseStart -eq 16)
$ref2 = (Get-Api "/reference/parse?q=$([uri]::EscapeDataString('诗篇23'))").data
Assert-True '解析 诗篇23' ($ref2.bookId -eq 19 -and $ref2.chapter -eq 23)

# 6. 日志写入与检索
$marker = "verify-$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
$body = @{ event = 'search'; message = "冒烟测试 $marker"; query = $marker; detail = 'verify.ps1' } | ConvertTo-Json
$posted = Invoke-RestMethod -Uri "$Base/logs" -Method Post -ContentType 'application/json; charset=utf-8' -Body $body
Assert-True 'POST /api/logs 返回 202 / code=0' ($posted.code -eq 0)
Start-Sleep -Milliseconds 1400
$logs = (Get-Api "/logs/search?q=$marker&size=5").data
Assert-True 'ES 按关键词检索到日志' ($logs.total -ge 1 -and -not $logs.degraded) "total=$($logs.total)"
$warn = (Get-Api '/logs/search?level=WARN&size=5').data
$badWarn = @($warn.items | Where-Object { $_.level -ne 'WARN' }).Count
Assert-True 'ES 按级别筛选 WARN' ($badWarn -eq 0) "bad=$badWarn"

# 7. 错误处理
try {
    $null = Get-Api "/chapters/43/999"
    Assert-True '越界章节返回 404' $false
} catch {
    Assert-True '越界章节返回 404' ($_.Exception.Response.StatusCode.value__ -eq 404)
}
try {
    $null = Get-Api "/reference/parse?q=$([uri]::EscapeDataString('不是经文引用'))"
    Assert-True '非法引用返回 400' $false
} catch {
    Assert-True '非法引用返回 400' ($_.Exception.Response.StatusCode.value__ -eq 400)
}

Write-Host ""
Write-Host "结果：$script:pass 通过 / $script:fail 失败" -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
