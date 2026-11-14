package com.bible.reader.util;

import com.bible.reader.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存门面：任何 Redis 异常都只 warn 一次并返回 null，让业务回落到数据库。
 * Redis 3.2 只使用基础命令（GET/SETEX/DEL）。
 */
@Slf4j
@Component
public class RedisCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public RedisCache(StringRedisTemplate redisTemplate,
                      ObjectMapper objectMapper,
                      AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> T get(String key, TypeReference<T> type) {
        if (!properties.getCache().isEnabled()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(fullKey(key));
            if (value == null || value.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            warnOnce("读取", key, e);
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        if (!properties.getCache().isEnabled() || value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(fullKey(key), objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            warnOnce("写入", key, e);
        }
    }

    public void delete(String key) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(fullKey(key));
        } catch (Exception e) {
            warnOnce("删除", key, e);
        }
    }

    private String fullKey(String key) {
        return properties.getCache().getKeyPrefix() + key;
    }

    private void warnOnce(String action, String key, Exception e) {
        if (warned.compareAndSet(false, true)) {
            log.warn("Redis 缓存{}失败，后续异常不再重复打印。key={}, cause={}", action, key, e.toString());
        }
    }
}
