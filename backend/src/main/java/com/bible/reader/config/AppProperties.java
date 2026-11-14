package com.bible.reader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * app.* 配置。默认值与规格保持一致，均可通过环境变量覆盖。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String version = "1.0.0";
    private Db db = new Db();
    private Cache cache = new Cache();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Cors cors = new Cors();

    @Data
    public static class Db {
        /** 逗号分隔的 host:port 候选，按顺序探测。 */
        private List<String> candidates = new ArrayList<>(List.of("127.0.0.1:3306", "127.0.0.1:3307"));
        private String database = "bible";
        private String username = "root";
        private String password = "11016135";
        private int poolSize = 10;
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        /** 只使用 GET/SETEX/DEL/PING 等基础命令，兼容 Redis 3.2。 */
        private String keyPrefix = "bible:v1:";
    }

    @Data
    public static class Elasticsearch {
        private String url = "http://127.0.0.1:9200";
        private boolean enabled = true;
        private String indexPrefix = "bible-logs";
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }
}
