# 精读圣经 · 后端实现规格（Spring Boot 3 + MyBatis + MySQL + Redis + Elasticsearch）

> 本文件是后端实现的唯一契约。请严格按此实现，前端（Vue 3）已按此 API 开发。
> 项目根目录：`D:\file\aidemo\bible`，后端目录：`D:\file\aidemo\bible\backend`。
> 数据已导入 MySQL 数据库 `bible`。不要修改 `db`、`frontend` 目录。

## 1. 运行环境

| 项目 | 值 |
| --- | --- |
| JDK | `D:\dev\Java\jdk-17`（Java 17） |
| Maven | `D:\dev\maven\apache-maven-3.9.9\bin\mvn.cmd`（请设置 `JAVA_HOME=D:\dev\Java\jdk-17`） |
| MySQL | `127.0.0.1:3306`，`root` / `1106135`，库 `bible`（3307 为回退实例，root/11016135） |
| Redis | `127.0.0.1:6379`，无密码（本机已启动） |
| Elasticsearch | `http://127.0.0.1:9200`，7.16.2，单节点，无认证（本机已启动） |
| 后端端口 | `8080` |

**重要**：系统 MySQL80（3306）已用 `root/1106135` 验证可连接，圣经数据已导入 3306 的 `bible` 库，作为主库。
项目仍保留 3307 独立实例（`root/11016135`）作为回退。
DataSource 支持多候选地址探测：依次尝试 `127.0.0.1:3306`、`127.0.0.1:3307`，
用 `DriverManager` 以 2 秒超时探测，选中第一个能连上的，再交给 HikariCP。
候选列表通过 `app.db.candidates` 配置（逗号分隔的 `host:port` 或 `host:port@password`，后者可携带该实例的密码）。
探测失败要打日志说明。不要用 Spring Boot 自动配置的 `spring.datasource.url` 直接固定 3306/3307。

## 2. 技术栈与依赖

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `mybatis-spring-boot-starter` **3.0.3**
- `mysql-connector-j`
- `spring-boot-starter-data-redis`（Lettuce）
- `com.fasterxml.jackson.core:jackson-databind`（web 自带）
- `org.projectlombok:lombok`（optional）
- 测试：`spring-boot-starter-test`

Spring Boot 版本用 **3.3.5**，Java 17，打包 `jar`，finalName `bible-reader`。
**不要**引入 Elasticsearch 官方客户端依赖，日志系统用 JDK 11 `java.net.http.HttpClient` 直接调用 ES REST API（ES 7.16 兼容），避免版本冲突。

## 3. 数据库结构（已存在，勿改）

```sql
book(id, name, short_name, english_name, english_abbr, aliases JSON文本, testament 'OT'/'NT',
     category, chapter_count, verse_count, sort_order)
chapter(book_id, chapter, verse_count, title_count)
verse(id, book_id, chapter, verse, content, FULLTEXT ft_content(content) WITH PARSER ngram)
section_title(id, book_id, chapter, verse, kind 'section'/'volume', content)
```

数据量：66 卷 / 1189 章 / 31102 节 / 2667 条标题。
`verse.content` 是中文和合本，没有 Strong 标记；可能有全角空格、中文引号，按原样返回。
`book.aliases` 是 JSON 数组字符串，如 `["创世记","创","Genesis","Gen","Ge","Gn"]`。

## 4. 包结构

```
com.bible.reader
├── BibleReaderApplication
├── config
│   ├── AppProperties          # app.* 配置
│   ├── DataSourceConfig       # 多候选探测
│   ├── MyBatisConfig          # @MapperScan
│   ├── RedisConfig            # StringRedisTemplate / ObjectMapper
│   ├── AsyncConfig            # logExecutor
│   └── WebConfig              # CORS + 请求日志拦截器注册
├── common
│   ├── ApiResponse<T>         # {code, message, data}
│   ├── BizException
│   └── GlobalExceptionHandler
├── controller
│   ├── BibleController        # /api/books, /api/books/{id}/chapters, /api/chapters/{b}/{c}
│   ├── SearchController       # /api/search, /api/reference/parse
│   ├── LogController          # POST /api/logs, GET /api/logs/search
│   └── MetaController         # /api/health, /api/stats
├── service
│   ├── BibleService
│   ├── SearchService
│   ├── ReferenceParser
│   ├── LogService
│   └── EsLogClient
├── mapper
│   ├── BookMapper
│   ├── ChapterMapper
│   ├── VerseMapper
│   └── SectionTitleMapper
├── model  (Book, Chapter, Verse, SectionTitle)
├── dto    (BookDto, ChapterDto, VerseDto, TitleDto, ChapterRefDto,
│           ChapterDetailDto, SearchItemDto, SearchResultDto, ReferenceDto,
│           LogEventDto, LogSearchResultDto, LogHitDto, HealthDto, StatsDto)
└── util
    ├── TextUtils            # 高亮区间、LIKE 转义、HTML 转义（如需要）
    └── RedisCache           # 安全 get/set，异常降级
```

XML 映射放在 `src/main/resources/mapper/*.xml`。
MyBatis 配置：`map-underscore-to-camel-case: true`、`mapper-locations: classpath:mapper/*.xml`、`type-aliases-package: com.bible.reader.model`。
所有 SQL 参数用 `#{}`（LIKE 的 `%` 通过 `CONCAT` 拼接参数）。

## 5. API 契约（必须逐字段一致）

统一响应体：

```json
{ "code": 0, "message": "ok", "data": {} }
```

成功 `code=0`；参数错误 HTTP 400 `code=400`；服务错误 HTTP 500 `code=500`；404 `code=404`。
Controller 用 `@RestController`，返回 `ApiResponse<T>`。日期时间用 ISO-8601 字符串（`2026-11-13T21:30:00+08:00`）。

### 5.1 `GET /api/books`

```json
{ "code": 0, "message": "ok", "data": [
  { "id": 1, "name": "创世记", "shortName": "创", "englishName": "Genesis",
    "englishAbbr": "Gen", "testament": "OT", "category": "律法书",
    "chapterCount": 50, "verseCount": 1533 }
] }
```

按 `sort_order` 升序；Redis 缓存 1 小时（key `bible:v1:books`）。

### 5.2 `GET /api/books/{bookId}/chapters`

```json
{ "code": 0, "message": "ok", "data": {
  "bookId": 19, "bookName": "诗篇", "chapterCount": 150,
  "chapters": [ { "chapter": 1, "verseCount": 6, "titleCount": 2 } ]
} }
```

书卷不存在返回 404。

### 5.3 `GET /api/chapters/{bookId}/{chapter}`

```json
{ "code": 0, "message": "ok", "data": {
  "book": { "...": "同 BookDto" },
  "chapter": 3,
  "verseCount": 36,
  "verses": [ { "verse": 1, "text": "有一个法利赛人，名叫尼哥底母……" } ],
  "titles": [ { "verse": 1, "kind": "section", "text": "耶稣与尼哥底母谈重生" } ],
  "prev": { "bookId": 43, "bookName": "约翰福音", "bookShortName": "约", "chapter": 2 },
  "next": { "bookId": 43, "bookName": "约翰福音", "bookShortName": "约", "chapter": 4 }
} }
```

- 章不存在（越界）返回 404。
- `prev` / `next` 跨书卷计算，首章/末章为 `null`。用缓存的 book 列表计算即可。
- 章节详情 Redis 缓存 30 分钟（key `bible:v1:chapter:{bookId}:{chapter}`），JSON 序列化。
- `titles` 按 `verse, id` 升序；`kind=volume` 是诗篇卷标，前端会特殊展示。

### 5.4 `GET /api/search?q=神爱世人&page=1&size=20&bookId=43`

- `q` 必填，trim 后 1~100 字符；`page` 默认 1（>=1）；`size` 默认 20（1~50）；`bookId` 可选。
- 先尝试 **经文引用解析**（见 5.5）。若 `q` 是合法引用：
  - 返回 `reference` 非空 + `items` 为该范围内的经文（最多 200 节，`total` = 实际节数），`page/size` 无分页意义（`size` 取 200）。
- 否则按关键词做 `LIKE` 搜索（多个关键词用空白分隔，AND 关系；每个关键词 `%kw%`，对 `\` `%` `_` 转义并使用 `ESCAPE '\\'`）。
- 排序：`book_id, chapter, verse` 升序。总数 `COUNT(*)`，`total` 为全部命中数。

```json
{ "code": 0, "message": "ok", "data": {
  "q": "神爱世人", "page": 1, "size": 20, "total": 1, "took": 12,
  "terms": ["神爱世人"],
  "reference": null,
  "items": [
    { "bookId": 43, "bookName": "约翰福音", "bookShortName": "约",
      "chapter": 3, "verse": 16, "ref": "约翰福音 3:16",
      "text": "“　神爱世人，甚至将他的独生子赐给他们……",
      "highlights": [[0, 5]] }
  ]
} }
```

- `highlights` 是 `[start, end)` 的字符下标（基于 Java `String` 的 UTF-16 下标，前端用 `slice` 渲染；中文 BMP 字符无代理对问题）。多个关键词的区间合并去重。
- `ref` 格式：`书名 章:节`；若是引用范围，`ref` 用 `书名 章:起-止`。
- 无结果时 `items: []`, `total: 0`。

### 5.5 `GET /api/reference/parse?q=约3:16-18&bookId=43`

- `bookId` 可选：当 `q` 只有 `章:节`（如 `3:16`）时，以该卷为上下文。
- 解析成功：

```json
{ "code": 0, "message": "ok", "data": {
  "bookId": 43, "bookName": "约翰福音", "bookShortName": "约",
  "chapter": 3, "verseStart": 16, "verseEnd": 18,
  "display": "约翰福音 3:16-18"
} }
```

- 解析失败返回 HTTP 400，`{ "code": 400, "message": "无法识别的经文引用", "data": null }`。

**引用解析规则**（`ReferenceParser`，启动时加载 book 列表与 aliases）：

1. 归一化：小写；去除所有空白（含 `\u3000`）和英文句点；全角 `：`→`:`；全角数字→半角；`章`/`篇`→`:`；`节`删除；`—`/`~`/`–`→`-`；去掉结尾多余的 `:`。
2. 别名列表按归一化长度降序，取能匹配输入前缀且剩余部分以数字开头的最长别名。
3. 支持：`约3`、`约3:16`、`约3:16-18`、`约翰福音3章16节`、`诗23篇`、`John 3:16`、`1Cor 13:4`、`3:16`（配合 bookId）。
4. 无别名且无 bookId → 失败；有 bookId 时剩余部分必须匹配。
5. 校验：`1 <= chapter <= book.chapterCount`；`verse >= 1`；`verseEnd >= verseStart`，超过该章节数则截断到 `verse_count`；`verseStart` 超过章节数则失败。
6. 若同一别名有多个书卷（当前数据无），取 id 最小。

提示：别名匹配必须“最长优先”，否则 `约` 会错误匹配 `约拿书`、`约伯记`、`约一`。
`ReferenceParser` 需要查询章节节数（可用 chapter 表或 verse count），可缓存。

### 5.6 `GET /api/stats`

```json
{ "code": 0, "message": "ok", "data": {
  "books": 66, "chapters": 1189, "verses": 31102, "titles": 2667,
  "oldTestamentBooks": 39, "newTestamentBooks": 27
} }
```

数值从数据库实时统计（可短缓存 10 分钟）。

### 5.7 `GET /api/health`

```json
{ "code": 0, "message": "ok", "data": {
  "status": "UP", "mysql": true, "redis": true, "elasticsearch": true,
  "version": "1.0.0", "time": "2026-11-13T21:30:00+08:00"
} }
```

- MySQL 用 `SELECT 1`，Redis 用 `PING`，ES 用 `GET /_cluster/health`（超时 500ms）。
- 任一依赖失败仍返回 HTTP 200，`status` 为 `DEGRADED`，对应布尔值 false（前端健康指示用）。

### 5.8 `POST /api/logs`

前端行为审计事件（阅读、搜索、复制、跳转等），异步写入 ES，不阻塞、不影响请求。

请求体：

```json
{ "event": "read", "message": "打开 约翰福音 3", "bookId": 43, "chapter": 3,
  "verse": 16, "query": null, "detail": "desktop" }
```

- `event` 白名单：`read`、`search`、`copy`、`jump`、`page_view`、`theme`、`font`、`error`、`api`。
- 服务端补充：`@timestamp`、`level`（INFO/WARN/ERROR）、`ip`、`userAgent`、`path`、`method`、`status`、`durationMs`。
- 返回 `202` + `{code:0,message:"accepted",data:null}`。
- 请求体校验：event 非空且在白名单；message 最长 500；query 最长 200。

### 5.9 `GET /api/logs/search?q=&level=&event=&from=&to=&page=1&size=20`

ES 日志检索：

```json
{ "code": 0, "message": "ok", "data": {
  "total": 123, "page": 1, "size": 20, "took": 8, "degraded": false,
  "items": [ {
    "timestamp": "2026-11-13T21:30:00+08:00", "level": "INFO", "event": "read",
    "message": "打开 约翰福音 3", "path": "/api/chapters/43/3", "method": "GET",
    "status": 200, "durationMs": 12, "ip": "127.0.0.1", "userAgent": "Mozilla/5.0",
    "bookId": 43, "chapter": 3, "verse": 16, "query": null
  } ],
  "stats": { "levels": {"INFO": 120, "WARN": 2, "ERROR": 1},
             "events": {"read": 60, "search": 30, "copy": 20, "jump": 13} }
} }
```

- `q` 对 `message`、`path`、`query`、`event` 做全文/通配匹配（用 `query_string`，字段 `message^2,path,query,event`，`default_operator: and`，`lenient: true`）。
- `level` 精确过滤（INFO/WARN/ERROR），`event` 精确过滤。
- `from`/`to` 是 ISO-8601 或 `yyyy-MM-dd`（按本地时区 +08:00 解释），过滤 `@timestamp`。
- 排序 `@timestamp` 降序；`page>=1`，`size` 1~100，默认 20。
- ES 不可用：返回内存环形缓冲（最多 500 条）中过滤后的数据，`degraded:true`，`stats` 依据缓冲计算；不得抛 500。
- 索引名：`bible-logs-yyyy.MM.dd`（按事件时间，Asia/Shanghai）；查询 `bible-logs-*`。
- 启动时用 `PUT /_index_template/bible-logs-template` 创建/更新模板（best effort，失败仅 warn）：
  - `index_patterns: ["bible-logs-*"]`
  - mapping：`@timestamp` date、`level` keyword、`event` keyword、`message` text（standard analyzer）、`path` keyword、`method` keyword、`status` integer、`durationMs` integer、`ip` keyword、`userAgent` text、`query` text、`bookId` integer、`chapter` integer、`verse` integer、`detail` text。

### 5.10 请求日志拦截器

`WebConfig` 注册 `HandlerInterceptor`（或 `OncePerRequestFilter`），对所有 `/api/**`（可排除 `/api/logs/search` 自身以免刷屏）异步记录：

- event=`api`；message=`GET /api/chapters/43/3 200 12ms`；path、method、status、durationMs、ip、userAgent、query（queryString）、level（>=500 ERROR，>=400 WARN，否则 INFO）。
- 异步线程池 `logExecutor`，队列满时丢弃并计数，不允许阻塞 Tomcat 线程。
- 启动时打印 banner：数据统计、MySQL 端口、Redis/ES 状态。

## 6. Redis 缓存与降级

- 使用 `StringRedisTemplate`，JSON 用注入的 `ObjectMapper`（含 JavaTimeModule）。
- `RedisCache` 封装 `get(key, TypeReference<T>)` / `set(key, value, ttl)` / `delete`，**所有异常捕获并 warn 一次**（避免刷屏），返回 null 让业务回落数据库。
- 关闭开关 `app.cache.enabled`（默认 true）；测试时可关。
- 缓存 key 前缀 `bible:v1:`；books TTL 1h，chapter TTL 30m，stats TTL 10m。
- 本机 Redis 3.2.100 较老：只用 `GET/SETEX/DEL/PING` 等基础命令，不要用 `UNLINK`、`HELLO`、JSON 模块、Stream。

## 7. Elasticsearch 日志客户端

`EsLogClient`：
- 用 `java.net.http.HttpClient`（connectTimeout 500ms，requestTimeout 2s），Jackson 序列化。
- `index(LogDoc)`：`POST {base}/bible-logs-{yyyy.MM.dd}/_doc`，`Content-Type: application/json`。
- `search(LogQuery)`：`POST {base}/bible-logs-*/_search`，body 用 Jackson `ObjectNode` 构造；解析 `hits.total.value`（ES7 对象）、`hits.hits[]._source`、`aggregations`。
- `ensureTemplate()`：`PUT {base}/_index_template/bible-logs-template`。
- `health()`：`GET {base}/_cluster/health`，2xx 即 true。
- 所有方法内部 catch，失败写 `log.warn`（限流：同一错误 30 秒只打一次），不影响主流程。
- 内存回退：`ConcurrentLinkedDeque<LogDoc>`，`addFirst`，超过 500 移除尾部；`searchFallback` 做包含匹配 + 过滤 + 分页。

**ES 索引文档示例**：

```json
{ "@timestamp": "2026-11-13T21:30:00.123+08:00", "level": "INFO", "event": "read",
  "message": "打开 约翰福音 3", "path": "/api/chapters/43/3", "method": "GET",
  "status": 200, "durationMs": 12, "ip": "127.0.0.1",
  "userAgent": "Mozilla/5.0 ...", "query": null, "bookId": 43, "chapter": 3,
  "verse": 16, "detail": "desktop", "app": "bible-reader" }
```

## 8. 配置（application.yml）

```yaml
server:
  port: 8080
  compression:
    enabled: true
    mime-types: application/json,text/html,text/css,application/javascript
spring:
  application:
    name: bible-reader
  jackson:
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      timeout: 800ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 4
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.bible.reader.model
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
app:
  db:
    candidates: 127.0.0.1:3306,127.0.0.1:3307@11016135
    database: bible
    username: root
    password: 1106135
    pool-size: 10
  cache:
    enabled: true
  elasticsearch:
    url: http://127.0.0.1:9200
    enabled: true
    index-prefix: bible-logs
  cors:
    allowed-origins: "*"
logging:
  level:
    com.bible.reader: info
```

支持环境变量覆盖（`APP_DB_PASSWORD` 等），但默认值必须如上。

## 9. 联调与验收

完成后必须执行：

1. `JAVA_HOME=D:\dev\Java\jdk-17` 下 `mvn -q -DskipTests package` 成功。
2. 启动 jar（8080），用 `Invoke-RestMethod` 验证：
   - `/api/books` → 66 条，第一条创世记 50 章。
   - `/api/chapters/43/3` → 36 节，`prev` 为 43:2，`next` 为 43:4；含标题。
   - `/api/chapters/19/117` → 2 节。
   - `/api/search?q=神爱世人` → total≥1，含 43:3:16，`highlights` 非空。
   - `/api/search?q=约3:16` → `reference` 非空，items 含 43:3:16。
   - `/api/search?q=诗23篇` → reference 诗篇 23，items 6 节。
   - `/api/reference/parse?q=John%203:16` → 43/3/16。
   - `/api/stats` → 66/1189/31102/2667。
   - `POST /api/logs` 后 `/api/logs/search?q=约翰` 能查到（ES 正常时 `degraded:false`）。
   - `/api/health` → mysql/redis/elasticsearch 均 true。
3. 中文字符不出现乱码；JSON 为 UTF-8。
4. 停止自己启动的后端进程，保留编译产物（我会统一启动）。**不要**修改 MySQL、Redis、ES 进程。
5. 在 `backend/README.md` 写明启动方式、端口、关键设计。

## 10. 代码质量要求

- 分层清晰，无 SQL 注入；所有用户输入校验；统一异常处理。
- 日志用 SLF4J，避免 `System.out`。
- 注释用中文，简洁说明"为什么"。
- 不要提交 `target/`、本机绝对路径写死在代码中（配置除外）。
- 不要改动前端目录；API 字段名严格按本契约，前端不再适配其他命名。
