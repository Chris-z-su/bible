package com.bible.reader.controller;

import com.bible.reader.common.ApiResponse;
import com.bible.reader.config.AppProperties;
import com.bible.reader.dto.HealthDto;
import com.bible.reader.dto.StatsDto;
import com.bible.reader.service.BibleService;
import com.bible.reader.service.EsLogClient;
import com.bible.reader.util.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@Slf4j
@RestController
@RequestMapping("/api")
public class MetaController {

    private final BibleService bibleService;
    private final StringRedisTemplate redisTemplate;
    private final EsLogClient esLogClient;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public MetaController(BibleService bibleService,
                          StringRedisTemplate redisTemplate,
                          EsLogClient esLogClient,
                          DataSource dataSource,
                          AppProperties properties) {
        this.bibleService = bibleService;
        this.redisTemplate = redisTemplate;
        this.esLogClient = esLogClient;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(2);
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<HealthDto> health() {
        boolean mysql = mysqlUp();
        boolean redis = redisUp();
        boolean elasticsearch = esLogClient.health();
        String status = mysql && redis && elasticsearch ? "UP" : "DEGRADED";
        HealthDto health = new HealthDto(status, mysql, redis, elasticsearch,
                properties.getVersion(), TextUtils.nowIso());
        return ApiResponse.ok(health);
    }

    @GetMapping("/stats")
    public ApiResponse<StatsDto> stats() {
        return ApiResponse.ok(bibleService.getStats());
    }

    private boolean mysqlUp() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (Exception e) {
            log.debug("MySQL 健康检查失败: {}", e.getMessage());
            return false;
        }
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
