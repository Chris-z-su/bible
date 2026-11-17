# Kibana 使用文档与查询语法教程

面向本项目的日志检索指南。Kibana 用于查看「精读圣经」后端写入 Elasticsearch 的运行日志（访问、搜索、阅读、错误等）。

## 1. 环境与访问

| 项目 | 值 |
| --- | --- |
| Kibana | 7.16.2，<http://127.0.0.1:5601> |
| 安装目录 | `D:\dev\kibana-7.16.2-windows-x86_64` |
| 配置文件 | `D:\dev\kibana-7.16.2-windows-x86_64\config\kibana.yml` |
| Elasticsearch | 7.16.2，`http://127.0.0.1:9200`，安装于 `D:\dev\elasticsearch` |
| 数据视图 | `bible-logs-*`（时间字段 `@timestamp`，已设为默认） |
| 默认时间范围 | 最近 7 天 |
| 界面语言 | 简体中文 |

架构：应用后端 → Elasticsearch 按天索引 `bible-logs-YYYY.MM.DD` → Kibana 查询展示。

### 启动与停止

推荐用项目脚本启动（后台运行，关闭终端不影响；已在运行会自动跳过）：

```powershell
powershell -ExecutionPolicy Bypass -File D:\file\aidemo\bible\deploy\start-service.ps1 -Name kibana
```

手动启动（前台运行，窗口需保持打开）：

```powershell
D:\dev\kibana-7.16.2-windows-x86_64\bin\kibana.bat

# 检查是否就绪
curl.exe -s http://127.0.0.1:5601/api/status

# 查看端口占用
Get-NetTCPConnection -State Listen -LocalPort 5601

# 停止：在启动窗口按 Ctrl+C，或结束对应的 node 进程
Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
  Where-Object { $_.CommandLine -like '*kibana*' } | ForEach-Object { Stop-Process -Id $_.ProcessId }
```

Kibana 版本必须与 Elasticsearch 完全一致（当前均为 7.16.2），否则启动时版本检查会报错。

## 2. 界面导航

| 菜单 | 地址 | 用途 |
| --- | --- | --- |
| Discover | `/app/discover` | 检索、过滤、查看日志原文（最常用） |
| Visualize | `/app/visualize` | 制作饼图、柱状图、折线图 |
| Dashboard | `/app/dashboard` | 组合多个图表成看板 |
| Dev Tools | `/app/dev_tools#/console` | 直接执行 Elasticsearch 请求 |
| Stack Management | `/app/management` | 数据视图、索引模式、高级设置 |

## 3. Discover 使用

1. 打开 <http://127.0.0.1:5601/app/discover>，默认数据视图为 `bible-logs-*`，时间范围「最近 7 天」。
2. 左上角搜索框输入查询语句（默认 KQL 语法），回车或点「刷新」。
3. 左侧「可用字段」点击 `+` 把字段加为列，常用：`@timestamp`、`level`、`event`、`message`、`path`、`status`、`durationMs`。
4. 展开某条日志可看完整 JSON；「查看单个文档」可看表格或 JSON。
5. 顶部时间选择器可切换最近 15 分钟 / 24 小时 / 自定义区间。
6. 右上「保存」可把当前查询+列+时间范围存为 Saved Search，方便下次直接打开。
7. 右上「共享 → CSV 报告」可导出当前结果。

### 日志字段说明

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `@timestamp` | date | 日志时间（含时区 `+08:00`） |
| `level` | keyword | `INFO` / `WARN` / `ERROR` |
| `event` | keyword | 事件类型：`api`、`read`、`search`、`page_view`、`copy`、`jump`、`theme`、`error` |
| `message` | text（ik 分词） | 可读描述，如「搜索「万物的结局」」 |
| `path` | keyword | 接口路径，如 `/api/logs` |
| `method` | keyword | HTTP 方法 |
| `status` | integer | HTTP 状态码，如 200 / 202 / 400 / 404 |
| `durationMs` | integer | 耗时（毫秒） |
| `ip` | keyword | 来源 IP |
| `userAgent` | text | 浏览器标识 |
| `app` | keyword | 应用名，固定 `bible-reader` |
| `requestBody` | text（ik 分词） | 接口入参 JSON，已脱敏，最多 2000 字符 |
| `responseBody` | text（ik 分词） | 接口出参 JSON，最多 2000 字符（超出截断） |
| `responseSize` | integer | 响应体字节数 |
| `bookId` / `chapter` / `verse` | integer | 关联的书卷 / 章 / 节 |
| `query` | text（ik 分词） | 搜索关键词 |
| `detail` | text | 附加说明，如「1 条结果，299ms」 |

区分要点：`keyword` 字段是精确值（区分大小写、不支持分词），`text` 字段会分词（中文按 ik 词典切词）。

### 查看某个接口的入参、出参、响应时间

1. 搜索 `event:api`，添加列 `method`、`path`、`status`、`durationMs`、`requestBody`、`responseBody`。
2. GET 请求的查询参数记录在 `query`（如 `bookId=19&chapter=23`）；POST/PUT 的 JSON 入参记录在 `requestBody`。
3. 展开日志 →「查看单个文档」可看完整字段；`responseSize` 为响应体字节数，`durationMs` 为服务端处理耗时（毫秒，不含网络传输）。
4. 常用检索：
   - 某接口的调用：`event:api and path:/api/chapters/19/23`
   - 按入参内容找：`event:api and requestBody:page_view`
   - 按出参内容找：`event:api and responseBody:耶和华`
   - 慢调用：`event:api and durationMs > 200`
5. 说明：入参、出参写入前会脱敏（`password`、`token`、`secret` 等字段替换为 `***`），并最多保存 2000 字符；`/api/logs/search` 本身不记录，避免日志递归。

## 4. KQL 查询语法

Kibana 默认查询语言为 KQL（Kibana Query Language），在 Discover 搜索框直接输入。

### 4.1 全文检索

```text
万物的结局          # 在所有可检索字段中查找该词
"赐平安的意念"      # 短语（整体匹配）
```

### 4.2 字段查询

```text
level:ERROR                    # 精确值（keyword）
event:search                   # 事件类型
status:404                     # 状态码
path:/api/chapters             # 完整路径
message:牧者                    # 中文分词匹配（text）
```

### 4.3 逻辑组合

```text
level:ERROR and status:500
level:(ERROR or WARN)
not level:INFO
event:api and status >= 400
(level:ERROR or level:WARN) and not path:/api/logs
```

运算符：`and`、`or`、`not`；用括号控制优先级。

### 4.4 存在性与通配符

```text
query:*                 # 字段存在（有值）
not query:*             # 字段缺失
message:牧*              # 前缀通配（* 任意字符，? 单个字符）
path:*search*
userAgent:*Edg*
```

### 4.5 范围查询

```text
status >= 400
durationMs > 500
@timestamp >= "2026-11-15" and @timestamp < "2026-11-16"
```

数字不加引号；日期用引号包裹。

### 4.6 特殊字符

值中出现 `\ ( ) : < > " * ? [ ] { } | ! & /` 等字符时，用双引号包起来或加反斜杠转义：

```text
path:"/api/chapters/1"
message:"（有古卷无“因为”至“阿们”等字。）"
```

### 4.7 常用查询配方（针对本项目）

| 目标 | 查询语句 |
| --- | --- |
| 所有错误 | `level:ERROR` |
| 错误与警告 | `level:(ERROR or WARN)` |
| 4xx/5xx 请求 | `status >= 400` |
| 慢请求（>500ms） | `durationMs > 500` |
| 搜索行为 | `event:search` |
| 某个关键词的搜索 | `query:盼望` |
| 阅读行为 | `event:read` |
| 章节访问 | `event:api and path:*chapters*` |
| 某书卷访问 | `bookId:19 and chapter:23` |
| 本机访问 | `ip:127.0.0.1` |
| 前端报错上报 | `event:error` |
| 收藏/复制行为 | `event:copy` |
| 排除健康检查 | `not path:*health*` |
| 带 JSON 入参的调用 | `event:api and requestBody:*` |
| 出参含某内容 | `event:api and responseBody:耶和华` |

### 4.8 KQL 与 Lucene 语法对照

搜索框左侧可切换为 Lucene。二者差异：

| 场景 | KQL | Lucene |
| --- | --- | --- |
| 逻辑 | `and` / `or` / `not` | `AND` / `OR` / `NOT`（`&&` `||` `!`） |
| 范围 | `status >= 400` | `status:[400 TO 599]` |
| 精确字段 | `level:ERROR` | `level:ERROR` |
| 字段存在 | `query:*` | `_exists_:query` |
| 正则 | 不支持 | `path:/\/api\/v[0-9]+/` |

日常用 KQL 即可；需要正则或复杂范围时切换到 Lucene。

## 5. 过滤器（Filters）

搜索框下方的「+ 添加筛选器」适合逐步收敛结果：

1. 选择字段（如 `level`），运算选 `是 / 不是 / 存在 / 范围内`。
2. 生成可开关的过滤条件，与查询语句是「与」关系。
3. 可在筛选器中临时排除（排除后变灰），便于对比。

适合场景：固定只看 `app: bible-reader`，再在搜索框里换关键词。

## 6. 可视化建议

在 Visualize 中基于 `bible-logs-*` 创建：

- **饼图**：按 `level` 拆分，快速看错误占比。
- **柱状图（日期直方图）**：X 轴 `@timestamp`（按小时/天），Y 轴计数，看日志量趋势。
- **数据表**：按 `path` 或 `event` 聚合计数，找高频接口。
- **折线图**：`durationMs` 平均值随时间变化，观察性能波动。

保存后可在 Dashboard 中组合成「运行监控」看板。

## 7. Dev Tools 常用请求

打开 <http://127.0.0.1:5601/app/dev_tools#/console>，逐条执行。

```http
# 集群健康
GET _cluster/health

# 日志索引列表与文档数
GET _cat/indices/bible-logs-*?v&s=index

# 最近 5 条日志
GET bible-logs-*/_search
{
  "size": 5,
  "sort": [{ "@timestamp": "desc" }]
}

# 错误日志
GET bible-logs-*/_search
{
  "size": 20,
  "query": { "term": { "level": "ERROR" } },
  "sort": [{ "@timestamp": "desc" }]
}

# 事件类型分布
GET bible-logs-*/_search
{
  "size": 0,
  "aggs": { "by_event": { "terms": { "field": "event", "size": 20 } } }
}

# 每天日志量
GET bible-logs-*/_search
{
  "size": 0,
  "aggs": {
    "per_day": {
      "date_histogram": { "field": "@timestamp", "calendar_interval": "day" }
    }
  }
}

# 最近一次带 JSON 入参的调用（含出参）
GET bible-logs-*/_search
{
  "size": 1,
  "query": { "exists": { "field": "requestBody" } },
  "sort": [{ "@timestamp": "desc" }]
}

# 慢请求 Top 10
GET bible-logs-*/_search
{
  "size": 10,
  "query": { "range": { "durationMs": { "gte": 500 } } },
  "sort": [{ "durationMs": "desc" }]
}
```

## 8. 运维与排错

### 8.1 日志位置

| 日志 | 路径 |
| --- | --- |
| Kibana 标准输出 | `.runtime\logs\kibana.out.log`（启动脚本重定向） |
| Kibana 自身日志 | `D:\dev\kibana-7.16.2-windows-x86_64\logs\kibana.log` |
| Elasticsearch 输出 | `.runtime\logs\elasticsearch.out.log` |
| Nginx 访问/错误 | `.runtime\nginx\access.log`、`.runtime\nginx\error.log` |

### 8.2 常见问题

| 现象 | 排查 |
| --- | --- |
| 打不开 5601 | 确认 `kibana.bat` 窗口未关闭；`Get-NetTCPConnection -LocalPort 5601` 是否有监听 |
| 启动报版本不匹配 | Kibana 与 ES 版本必须一致（7.16.2） |
| Discover 无数据 | 时间范围是否覆盖日志日期；数据视图是否为 `bible-logs-*`；`GET _cat/indices/bible-logs-*` 是否有索引 |
| 有索引但查不到 | 检查查询语法或是否误加了过滤器；点筛选器的垃圾桶清除 |
| 中文搜不到 | `message`/`query` 为 ik 分词字段，换用完整词或更短词；也可试 `message:*关键词*` |
| Kibana 状态 yellow | 单节点副本未分配属正常（数据索引副本数为 0，可忽略） |
| 页面很卡 | Kibana 前端资源较大，首次加载较慢；缩小时间范围或字段列 |
| 端口冲突 | 修改 `kibana.yml` 中 `server.port` 后重启 |

### 8.3 索引与保留

- 后端按天写入索引 `bible-logs-YYYY.MM.DD`，新日志自动生成新索引。
- 需要清理旧日志时（示例：删除 30 天前）：

```powershell
# 先确认再删除，操作不可恢复
curl.exe -X DELETE "http://127.0.0.1:9200/bible-logs-2026.10.*"
```

### 8.4 安全说明

- Kibana 仅监听 `127.0.0.1`，Elasticsearch 未开启认证，属本机开发配置。
- 不要将 5601 / 9200 暴露到公网或不受信网络；如需局域网访问，应同时启用 ES 安全认证并配置 Kibana 账号。

## 9. 速查表

```text
全文         万物的结局            短语        "赐平安的意念"
等值         level:ERROR          列表        level:(ERROR or WARN)
排除         not level:INFO       存在        query:*
通配         path:*search*        范围        status >= 400
数值         durationMs > 500     日期        @timestamp >= "2026-11-15"
组合         event:api and status >= 400
```

最常用入口：<http://127.0.0.1:5601/app/discover>，搜索 `level:(ERROR or WARN)` 查看异常，搜索 `event:search` 查看用户检索词。
