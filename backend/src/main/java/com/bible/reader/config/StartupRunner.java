package com.bible.reader.config;

import com.bible.reader.dto.StatsDto;
import com.bible.reader.service.BibleService;
import com.bible.reader.service.EsLogClient;
import com.bible.reader.service.ReferenceParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动预热与 banner：数据统计、实际选中的 MySQL、Redis/ES 状态。
 */
@Slf4j
@Component
@Order(2)
public class StartupRunner implements ApplicationRunner {

    private final ReferenceParser referenceParser;
    private final BibleService bibleService;
    private final EsLogClient esLogClient;
    private final StringRedisTemplate redisTemplate;
    private final DatabaseInfo databaseInfo;
    private final AppProperties properties;

    @Value("${server.port:8080}")
    private int serverPort;

    public StartupRunner(ReferenceParser referenceParser,
                         BibleService bibleService,
                         EsLogClient esLogClient,
                         StringRedisTemplate redisTemplate,
                         DatabaseInfo databaseInfo,
                         AppProperties properties) {
        this.referenceParser = referenceParser;
        this.bibleService = bibleService;
        this.esLogClient = esLogClient;
        this.redisTemplate = redisTemplate;
        this.databaseInfo = databaseInfo;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            referenceParser.ensureLoaded();
        } catch (Exception e) {
            log.warn("引用解析器预热失败，将在首次请求时重试: {}", e.toString());
        }
        try {
            esLogClient.ensureTemplate();
        } catch (Exception e) {
            log.warn("Elasticsearch 日志模板初始化失败: {}", e.toString());
        }

        String statsText = "不可用";
        try {
            bibleService.listBooks();
            StatsDto stats = bibleService.getStats();
            statsText = stats.getBooks() + " 卷 / " + stats.getChapters() + " 章 / "
                    + stats.getVerses() + " 节 / " + stats.getTitles() + " 标题";
        } catch (Exception e) {
            log.warn("启动统计查询失败: {}", e.toString());
        }

        boolean redisUp = redisUp();
        boolean esUp = esLogClient.health();
        log.info("""

                ============================================================
                 精读圣经后端已启动
                 数据统计: {}
                 MySQL   : {}（多候选探测选中）
                 Redis   : {}
                 ES      : {}（索引模板 {}-template，内存回退 {} 条）
                 监听端口: {}
                ============================================================""",
                statsText,
                databaseInfo.display(),
                redisUp ? "UP" : "DOWN",
                properties.getElasticsearch().isEnabled() ? (esUp ? "UP" : "DOWN") : "DISABLED",
                properties.getElasticsearch().getIndexPrefix(),
                esLogClient.fallbackBufferSize(),
                serverPort);
    }

    private boolean redisUp() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            return pong != null && "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.debug("Redis 健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
