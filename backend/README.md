# 精读圣经 · 后端（bible-reader）

Spring Boot 3.3.5 + Java 17 + MyBatis 3.0.3 + MySQL + Redis + Elasticsearch 7.16 的后端实现。
接口契约见 `../docs/BACKEND_SPEC.md`，本目录只实现后端，不修改 `db`、`docs`、`frontend`。

## 1. 启动方式

```powershell
$env:JAVA_HOME = 'D:\dev\Java\jdk-17'
Set-Location 'D:\file\aidemo\bible\backend'
& 'D:\dev\maven\apache-maven-3.9.9\bin\mvn.cmd' -q -DskipTests package
& 'D:\dev\Java\jdk-17\bin\java.exe' -jar target\bible-reader.jar
```

- 默认端口：**8080**（`server.port`，可用 `--server.port=xxxx` 覆盖）。
- 产物：`target/bible-reader.jar`（Spring Boot 可执行 jar，finalName `bible-reader`）。
- 依赖服务：MySQL 3306（库 `bible`，`root/1106135`）、Redis 6379、Elasticsearch 9200，均需提前启动。
- 启动时会打印 banner：数据统计、实际选中的 MySQL 地址、Redis/ES 状态、监听端口。

常用环境变量覆盖（默认值已按规格写好）：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_DB_CANDIDATES` | `127.0.0.1:3306,127.0.0.1:3307@11016135` | MySQL 候选，逗号分隔；`@` 后可指定该实例密码 |
| `APP_DB_DATABASE` | `bible` | 数据库名 |
| `APP_DB_USERNAME` / `APP_DB_PASSWORD` | `root` / `1106135` | 账号密码（3306 主库） |
| `APP_CACHE_ENABLED` | `true` | Redis 缓存开关 |
| `APP_ES_URL` / `APP_ES_ENABLED` | `http://127.0.0.1:9200` / `true` | ES 地址与开关 |
| `APP_CORS_ALLOWED_ORIGINS` | `*` | CORS 允许来源 |

## 2. API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/books` | 66 卷，按 `sort_order` 升序，Redis 缓存 1h |
| GET | `/api/books/{bookId}/chapters` | 书卷章列表，书卷不存在 404 |
| GET | `/api/chapters/{bookId}/{chapter}` | 章节详情 + 标题 + 跨卷 prev/next，缓存 30m |
| GET | `/api/search?q=&page=&size=&bookId=` | 引用优先，否则多关键词 LIKE（AND） |
| GET | `/api/reference/parse?q=&bookId=` | 引用解析，失败 400 |
| GET | `/api/stats` | 实时统计，缓存 10m |
| GET | `/api/health` | MySQL/Redis/ES 健康，依赖失败仍 HTTP 200 |
| POST | `/api/logs` | 前端审计事件，202，异步写 ES |
| GET | `/api/logs/search?q=&level=&event=&from=&to=&page=&size=` | ES 日志检索，ES 故障走内存回退 |

统一响应：`{ "code": 0, "message": "ok", "data": ... }`；参数错误 400，服务错误 500，404 同理。
字段名严格按规格，没有重命名。`reference`、`prev`、`next`、`query` 等可空字段显式输出 `null`。

## 3. 设计要点

### 3.1 MySQL 多候选探测

`DataSourceConfig` 不用 `spring.datasource.url` 自动配置，而是按 `app.db.candidates`
依次用 `DriverManager` 探测（`DriverManager.setLoginTimeout(2)` + JDBC `connectTimeout=2000`），
选中第一个可用地址后交给 HikariCP。默认主库为系统 MySQL80 **3306**（`root/1106135`）；
3307 使用 `host:port@password` 形式携带自己的密码，作为 3306 不可用时的回退。
实际选中的 URL 通过 `DatabaseInfo` 暴露给启动 banner。

### 3.2 Redis 缓存与降级

`RedisCache` 封装 `get(TypeReference<T>)` / `set(key, value, ttl)` / `delete`：
- 只用 `GET/SETEX/DEL` 等基础命令，兼容 Redis 3.2.100；
- 所有异常捕获并只 warn 一次，返回 `null` 让业务回落数据库；
- 可通过 `app.cache.enabled=false` 整体关闭；
- key 前缀 `bible:v1:`，books 1h、chapter 30m、stats 10m。

### 3.3 Elasticsearch 日志

- 不引入 ES 官方客户端，使用 JDK 11 `java.net.http.HttpClient` 调用 REST；
  connectTimeout 500ms，普通请求 2s，健康检查 500ms。
- 启动时 best-effort `PUT /_index_template/bible-logs-template`，索引名 `bible-logs-yyyy.MM.dd`（Asia/Shanghai），查询 `bible-logs-*`。
- `message`、`query` 使用 `ik_max_word`（本机已装 analysis-ik，比 standard 更适合中文），其余字段按规格 mapping。
- `LogService` 用 `logExecutor` 异步提交；队列满时 `CountingRejectedExecutionHandler` 丢弃并计数，不阻塞 Tomcat。
- `EsLogClient` 内维护最多 500 条的 `ConcurrentLinkedDeque` 内存回退：ES 停机/超时/禁用时
  `GET /api/logs/search` 返回 `degraded:true`，按包含匹配 + level/event/时间过滤 + 分页，`stats` 依据缓冲计算。
- 所有 ES 失败只 warn，同一类错误 30 秒最多打印一次。

### 3.4 引用解析

`ReferenceParser` 启动时加载 66 卷、368 个别名、1189 章的节数并缓存：
- 归一化：小写、去空白/句点、全角数字/冒号转半角、`章/篇`→`:`、删 `节`、`—/~/–`→`-`、去尾部 `:`；
- 别名按归一化长度降序匹配，保证 `约` 不会误匹配 `约拿书/约伯记/约一`；
- 支持 `约3`、`约3:16`、`约3:16-18`、`约翰福音3章16节`、`诗23篇`、`诗篇23`、`John 3:16`、`1Cor 13:4`、`3:16`（配合 bookId）；
- 越界/非法引用返回空；`parse` 接口返回 400 `无法识别的经文引用`。

### 3.5 请求日志与异常

- `WebConfig` 注册 `RequestLogInterceptor`，覆盖 `/api/**`，排除 `/api/logs/search`；
  记录 `event=api`、`METHOD path status durationMs`、path/method/status/durationMs/ip/userAgent/query，
  level 按 `>=500 ERROR / >=400 WARN / 其余 INFO`。
- `ApiBodyCaptureFilter`（`OncePerRequestFilter`，优先级 `HIGHEST_PRECEDENCE + 10`）缓存 `/api/**` 的请求体（上限 8KB）与响应体，
  拦截器在 `afterCompletion` 读取并写入 `requestBody`、`responseBody`、`responseSize`；过滤器结束时 `copyBodyToResponse()` 保证客户端响应不变。
- 入参/出参写入前脱敏（`password/token/secret` 等替换为 `***`）并截断到 2000 字符；仅记录 JSON/XML/文本类型，`/api/logs/search` 排除，避免日志递归。
- `GlobalExceptionHandler` 统一输出 `ApiResponse`，不泄漏堆栈；未知接口 404、参数错误 400。
- 全项目使用 SLF4J，无 `System.out`。

## 4. 目录结构

```text
backend/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/bible/reader/
    │   ├── BibleReaderApplication.java
    │   ├── config/      AppProperties, DataSourceConfig, MyBatisConfig, RedisConfig,
    │   │                AsyncConfig, WebConfig, RequestLogInterceptor, ApiBodyCaptureFilter,
    │   │                StartupRunner
    │   ├── common/      ApiResponse, BizException, GlobalExceptionHandler
    │   ├── controller/  BibleController, SearchController, LogController, MetaController
    │   ├── service/     BibleService, SearchService, ReferenceParser, LogService, EsLogClient
    │   ├── mapper/      BookMapper, ChapterMapper, VerseMapper, SectionTitleMapper
    │   ├── model/       Book, Chapter, Verse, SectionTitle, LogDoc, LogQuery
    │   ├── dto/         BookDto, ChapterDto, ... HealthDto, StatsDto
    │   └── util/        TextUtils, RedisCache
    └── resources/
        ├── application.yml
        └── mapper/*.xml
```

## 5. 验收结果

在 JDK 17 下执行 `mvn -q -DskipTests package`：**BUILD SUCCESS**，产物
`target/bible-reader.jar`（约 37.9 MB）。

启动 jar 后用 `Invoke-RestMethod` 实测（最终构建 12/12 通过）：

- `/api/books`：66 条，首条创世记 50 章；
- `/api/chapters/43/3`：36 节，prev=43:2，next=43:4，3 条标题；
- `/api/chapters/19/117`：2 节；
- `/api/search?q=神爱世人`：total=1，命中 43:3:16，highlights `[[2,6]]`；
- `/api/search?q=约3:16`：reference 非空，items 含 43:3:16；
- `/api/search?q=诗23篇`：reference 诗篇 23，6 节；
- `/api/reference/parse?q=John 3:16`：43/3/16；`诗篇23`、`约拿书1:1` 等最长别名用例通过；
- `/api/stats`：66/1189/31102/2667；
- `POST /api/logs` 后 `/api/logs/search?q=约翰`：`degraded:false`，能查到刚写入的事件；
- `/api/health`：mysql/redis/elasticsearch 均 true，status=UP；
- 中文 JSON 为 UTF-8（`Content-Type: application/json;charset=UTF-8`），无乱码；
- 额外用 `--server.port=8081 --app.elasticsearch.enabled=false` 验证内存回退：
  `degraded:true`、health `DEGRADED`、`stats` 来自缓冲，验证后已停止该进程。

验收完成后已停止自测后端进程，8080 交由主流程统一启动，保留 `target/` 编译产物。

## 6. 已知限制

1. 引用范围目前限定在同一章内（规格未要求跨章区间，如 `约3:16-4:2`）。
2. ES 默认刷新间隔 1s：`POST /api/logs` 后立即检索可能查不到，建议 1s 后再查；不影响最终一致。
3. 内存回退最多 500 条、进程重启即丢失，多实例之间不共享；ES 恢复后不会自动补写缓冲。
4. 日志检索深度受 ES `max_result_window`（10000）限制，超深分页会退回内存回退结果（`degraded:true`）。
5. 本项目是内网只读圣经服务，未实现鉴权；CORS 默认 `*`，生产环境应按需收紧。
6. 3307 回退实例需要手动/由 `start-all.ps1` 启动；正常情况主库为 3306。
7. 缓存不做主动刷新（圣经数据静态）；数据导入后如需立即生效，重启或等待 TTL。
