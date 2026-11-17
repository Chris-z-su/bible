package com.bible.reader.service;

import com.bible.reader.config.AppProperties;
import com.bible.reader.dto.LogHitDto;
import com.bible.reader.dto.LogSearchResultDto;
import com.bible.reader.model.LogDoc;
import com.bible.reader.model.LogQuery;
import com.bible.reader.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Elasticsearch 日志客户端：仅用 JDK HttpClient 调用 REST，避免官方客户端版本冲突。
 * ES 故障时自动回退到内存环形缓冲（最多 500 条），不影响主流程。
 */
@Slf4j
@Component
public class EsLogClient {

    private static final DateTimeFormatter INDEX_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final int MAX_FALLBACK = 500;
    private static final long WARN_INTERVAL_MS = 30_000L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String indexPrefix;
    private final boolean enabled;
    private final Deque<LogDoc> fallbackBuffer = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> warnTimes = new ConcurrentHashMap<>();

    public EsLogClient(AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(properties.getElasticsearch().getUrl());
        this.indexPrefix = properties.getElasticsearch().getIndexPrefix();
        this.enabled = properties.getElasticsearch().isEnabled();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
    }

    /**
     * 索引模板为 best effort：失败只 warn，不影响应用启动。
     */
    public void ensureTemplate() {
        if (!enabled) {
            log.info("Elasticsearch 已禁用，日志检索将使用内存回退");
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(templateBody());
            HttpResponse<String> response = sendJson("PUT",
                    "/_index_template/" + indexPrefix + "-template", body, 2000);
            if (response.statusCode() / 100 == 2) {
                log.info("Elasticsearch 日志模板已就绪：{}-template", indexPrefix);
            } else {
                warnLimited("template", "Elasticsearch 日志模板创建失败 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
        } catch (Exception e) {
            warnLimited("template", "Elasticsearch 日志模板创建异常：" + e);
        }
    }

    /**
     * 写入日志：先进入内存缓冲，再异步线程调用 ES。失败不影响调用方。
     */
    public void index(LogDoc doc) {
        if (doc == null) {
            return;
        }
        if (doc.getTimestamp() == null) {
            doc.setTimestamp(TextUtils.nowIso());
        }
        if (doc.getApp() == null) {
            doc.setApp("bible-reader");
        }
        addToFallback(doc);
        if (!enabled) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(doc);
            HttpResponse<String> response = sendJson("POST", "/" + indexName(doc.getTimestamp()) + "/_doc", body, 2000);
            if (response.statusCode() / 100 != 2) {
                warnLimited("index", "Elasticsearch 日志写入失败 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            warnLimited("index", "Elasticsearch 日志写入异常：" + e);
        }
    }

    public LogSearchResultDto search(LogQuery query) {
        long startedAt = System.nanoTime();
        if (!enabled) {
            return fallback(query, startedAt);
        }
        try {
            String body = objectMapper.writeValueAsString(buildSearchBody(query));
            String path = "/" + indexPrefix
                    + "-*/_search?ignore_unavailable=true&allow_no_indices=true";
            HttpResponse<String> response = sendJson("POST", path, body, 2000);
            if (response.statusCode() == 404 && response.body() != null
                    && response.body().contains("index_not_found_exception")) {
                return emptyResult(query, startedAt);
            }
            if (response.statusCode() / 100 != 2) {
                warnLimited("search", "Elasticsearch 日志检索失败 HTTP " + response.statusCode()
                        + "，使用内存回退：" + abbreviate(response.body()));
                return fallback(query, startedAt);
            }
            return parseSearchResponse(response.body(), query, startedAt);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            warnLimited("search", "Elasticsearch 日志检索异常，使用内存回退：" + e);
            return fallback(query, startedAt);
        }
    }

    public boolean health() {
        if (!enabled) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/_cluster/health"))
                    .timeout(Duration.ofMillis(500))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            warnLimited("health", "Elasticsearch 健康检查失败：" + e);
            return false;
        }
    }

    public int fallbackBufferSize() {
        return fallbackBuffer.size();
    }

    private ObjectNode buildSearchBody(LogQuery query) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("from", (long) (query.getPage() - 1) * query.getSize());
        body.put("size", query.getSize());
        body.put("track_total_hits", true);

        ObjectNode sortItem = body.putArray("sort").addObject();
        sortItem.putObject("@timestamp").put("order", "desc");

        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        if (query.getQ() != null && !query.getQ().isBlank()) {
            ObjectNode queryString = must.addObject().putObject("query_string");
            queryString.put("query", query.getQ());
            ArrayNode fields = queryString.putArray("fields");
            fields.add("message^2");
            fields.add("path");
            fields.add("query");
            fields.add("event");
            queryString.put("default_operator", "and");
            queryString.put("lenient", true);
        } else {
            must.addObject().putObject("match_all");
        }

        ArrayNode filter = bool.putArray("filter");
        if (query.getLevel() != null && !query.getLevel().isBlank()) {
            filter.addObject().putObject("term").put("level", query.getLevel().toUpperCase(Locale.ROOT));
        }
        if (query.getEvent() != null && !query.getEvent().isBlank()) {
            filter.addObject().putObject("term").put("event", query.getEvent());
        }
        if (query.getFrom() != null || query.getTo() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("@timestamp");
            if (query.getFrom() != null) {
                range.put("gte", TextUtils.formatIso(query.getFrom()));
            }
            if (query.getTo() != null) {
                range.put("lte", TextUtils.formatIso(query.getTo()));
            }
        }

        ObjectNode aggs = body.putObject("aggs");
        aggs.putObject("levels").putObject("terms").put("field", "level").put("size", 10);
        aggs.putObject("events").putObject("terms").put("field", "event").put("size", 50);
        return body;
    }

    private LogSearchResultDto parseSearchResponse(String responseBody, LogQuery query, long startedAt) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode hitsNode = root.path("hits");
        long total;
        if (hitsNode.path("total").isObject()) {
            total = hitsNode.path("total").path("value").asLong(0);
        } else {
            total = hitsNode.path("total").asLong(0);
        }
        long took = root.path("took").asLong(elapsedMs(startedAt));

        List<LogHitDto> items = new ArrayList<>();
        JsonNode hits = hitsNode.path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode source = hit.get("_source");
                if (source == null || source.isNull()) {
                    continue;
                }
                LogDoc doc = objectMapper.treeToValue(source, LogDoc.class);
                items.add(toHit(doc));
            }
        }

        JsonNode aggregations = root.path("aggregations");
        Map<String, Long> levels = buckets(aggregations, "levels");
        Map<String, Long> events = buckets(aggregations, "events");
        return new LogSearchResultDto(total, query.getPage(), query.getSize(), took,
                Boolean.FALSE, items, new LogSearchResultDto.Stats(levels, events));
    }

    private Map<String, Long> buckets(JsonNode aggregations, String name) {
        Map<String, Long> result = new LinkedHashMap<>();
        JsonNode buckets = aggregations.path(name).path("buckets");
        if (buckets.isArray()) {
            for (JsonNode bucket : buckets) {
                result.put(bucket.path("key").asText(), bucket.path("doc_count").asLong());
            }
        }
        return result;
    }

    private LogSearchResultDto fallback(LogQuery query, long startedAt) {
        String keyword = query.getQ() == null ? "" : query.getQ().trim().toLowerCase(Locale.ROOT);
        List<LogDoc> filtered = new ArrayList<>();
        for (LogDoc doc : fallbackBuffer) {
            if (!matchesQuery(doc, keyword)) {
                continue;
            }
            if (query.getLevel() != null && !query.getLevel().isBlank()
                    && !query.getLevel().equalsIgnoreCase(doc.getLevel())) {
                continue;
            }
            if (query.getEvent() != null && !query.getEvent().isBlank()
                    && !query.getEvent().equalsIgnoreCase(doc.getEvent())) {
                continue;
            }
            OffsetDateTime timestamp = parseTimestamp(doc.getTimestamp());
            if (query.getFrom() != null && (timestamp == null || timestamp.isBefore(query.getFrom()))) {
                continue;
            }
            if (query.getTo() != null && (timestamp == null || timestamp.isAfter(query.getTo()))) {
                continue;
            }
            filtered.add(doc);
        }
        filtered.sort((left, right) -> {
            OffsetDateTime a = parseTimestamp(left.getTimestamp());
            OffsetDateTime b = parseTimestamp(right.getTimestamp());
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            return b.compareTo(a);
        });

        int total = filtered.size();
        long offset = (long) (query.getPage() - 1) * query.getSize();
        List<LogHitDto> items = new ArrayList<>();
        if (offset < total) {
            int start = (int) offset;
            int end = Math.min(total, start + query.getSize());
            for (LogDoc doc : filtered.subList(start, end)) {
                items.add(toHit(doc));
            }
        }
        return new LogSearchResultDto((long) total, query.getPage(), query.getSize(),
                elapsedMs(startedAt), Boolean.TRUE, items, statsOf(filtered));
    }

    private LogSearchResultDto emptyResult(LogQuery query, long startedAt) {
        return new LogSearchResultDto(0L, query.getPage(), query.getSize(),
                elapsedMs(startedAt), Boolean.FALSE, new ArrayList<>(),
                new LogSearchResultDto.Stats(new LinkedHashMap<>(), new LinkedHashMap<>()));
    }

    private boolean matchesQuery(LogDoc doc, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return contains(doc.getMessage(), keyword)
                || contains(doc.getPath(), keyword)
                || contains(doc.getQuery(), keyword)
                || contains(doc.getEvent(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private LogSearchResultDto.Stats statsOf(List<LogDoc> docs) {
        Map<String, Long> levels = new LinkedHashMap<>();
        Map<String, Long> events = new LinkedHashMap<>();
        for (LogDoc doc : docs) {
            if (doc.getLevel() != null) {
                levels.merge(doc.getLevel(), 1L, Long::sum);
            }
            if (doc.getEvent() != null) {
                events.merge(doc.getEvent(), 1L, Long::sum);
            }
        }
        return new LogSearchResultDto.Stats(sortByCount(levels), sortByCount(events));
    }

    private Map<String, Long> sortByCount(Map<String, Long> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private LogHitDto toHit(LogDoc doc) {
        return new LogHitDto(doc.getTimestamp(), doc.getLevel(), doc.getEvent(), doc.getMessage(),
                doc.getPath(), doc.getMethod(), doc.getStatus(), doc.getDurationMs(), doc.getIp(),
                doc.getUserAgent(), doc.getBookId(), doc.getChapter(), doc.getVerse(), doc.getQuery());
    }

    private void addToFallback(LogDoc doc) {
        fallbackBuffer.addFirst(doc);
        while (fallbackBuffer.size() > MAX_FALLBACK) {
            fallbackBuffer.pollLast();
        }
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private String indexName(String timestamp) {
        LocalDate date = LocalDate.now(TextUtils.ZONE);
        if (timestamp != null) {
            try {
                date = OffsetDateTime.parse(timestamp).atZoneSameInstant(TextUtils.ZONE).toLocalDate();
            } catch (Exception ignored) {
                // 时间不可解析时退回当前日期
            }
        }
        return indexPrefix + "-" + date.format(INDEX_DATE);
    }

    private ObjectNode templateBody() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("index_patterns").add(indexPrefix + "-*");
        root.put("priority", 200);
        ObjectNode template = root.putObject("template");
        ObjectNode settings = template.putObject("settings");
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);
        ObjectNode properties = template.putObject("mappings").putObject("properties");
        addField(properties, "@timestamp", "date", null);
        addField(properties, "level", "keyword", null);
        addField(properties, "event", "keyword", null);
        // 已安装 analysis-ik，中文用 ik_max_word 比 standard 更合适
        addField(properties, "message", "text", "ik_max_word");
        addField(properties, "path", "keyword", null);
        addField(properties, "method", "keyword", null);
        addField(properties, "status", "integer", null);
        addField(properties, "durationMs", "integer", null);
        addField(properties, "ip", "keyword", null);
        addField(properties, "userAgent", "text", null);
        addField(properties, "query", "text", "ik_max_word");
        addField(properties, "bookId", "integer", null);
        addField(properties, "chapter", "integer", null);
        addField(properties, "verse", "integer", null);
        addField(properties, "detail", "text", null);
        addField(properties, "app", "keyword", null);
        // 接口入参/出参（已脱敏、截断，最多 2000 字符）
        addField(properties, "requestBody", "text", "ik_max_word");
        addField(properties, "responseBody", "text", "ik_max_word");
        addField(properties, "responseSize", "integer", null);
        return root;
    }

    private void addField(ObjectNode properties, String name, String type, String analyzer) {
        ObjectNode field = properties.putObject(name);
        field.put("type", type);
        if (analyzer != null) {
            field.put("analyzer", analyzer);
        }
    }

    private HttpResponse<String> sendJson(String method, String path, String body, long timeoutMs)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json");
        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private void warnLimited(String key, String message) {
        long now = System.currentTimeMillis();
        Long previous = warnTimes.get(key);
        if (previous == null) {
            if (warnTimes.putIfAbsent(key, now) == null) {
                log.warn(message);
            }
        } else if (now - previous >= WARN_INTERVAL_MS && warnTimes.replace(key, previous, now)) {
            log.warn(message);
        }
    }

    private String abbreviate(String value) {
        return TextUtils.truncate(value, 300);
    }

    private static String trimTrailingSlash(String url) {
        String value = (url == null || url.isBlank()) ? "http://127.0.0.1:9200" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
