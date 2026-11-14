package com.bible.reader.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * 多候选 MySQL 探测：默认主库为系统实例 3306；若不可用则回落到项目独立实例 3307。
 * 候选格式：host:port 或 host:port@password（允许不同实例使用不同密码）。
 * 不依赖 spring.datasource.url 自动配置，选中后再交给 HikariCP。
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(AppProperties properties) {
        String url = probe(properties);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(properties.getDb().getUsername());
        config.setPassword(properties.getDb().getPassword());
        config.setMaximumPoolSize(Math.max(1, properties.getDb().getPoolSize()));
        config.setMinimumIdle(Math.min(2, Math.max(1, properties.getDb().getPoolSize())));
        config.setPoolName("bible-hikari");
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(2000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }

    @Bean
    public DatabaseInfo databaseInfo(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return DatabaseInfo.fromJdbcUrl(hikari.getJdbcUrl());
        }
        return DatabaseInfo.fromJdbcUrl("");
    }

    private String probe(AppProperties properties) {
        AppProperties.Db db = properties.getDb();
        List<String> candidates = db.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("app.db.candidates 为空，无法连接 MySQL");
        }
        // 2 秒登录超时，避免不可达地址拖慢启动
        DriverManager.setLoginTimeout(2);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String host;
            int port;
            String raw = candidate.trim();
            String candidatePassword = db.getPassword();
            // 支持 host:port@password，便于 3306/3307 使用不同密码
            int at = raw.indexOf('@');
            if (at > 0) {
                candidatePassword = raw.substring(at + 1);
                raw = raw.substring(0, at);
            }
            String[] parts = raw.split(":", 2);
            host = parts[0];
            try {
                port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3306;
            } catch (NumberFormatException e) {
                log.warn("MySQL 候选地址格式非法，已跳过: {}", candidate);
                continue;
            }
            String url = buildJdbcUrl(host, port, db.getDatabase());
            try (Connection connection = DriverManager.getConnection(url, db.getUsername(), candidatePassword)) {
                if (connection.isValid(2)) {
                    log.info("MySQL 连接成功，选中候选 {}:{}，库 {}", host, port, db.getDatabase());
                    return url;
                }
                log.warn("MySQL 候选 {}:{} 连接无效，继续尝试下一个", host, port);
            } catch (SQLException e) {
                log.warn("MySQL 候选 {}:{} 探测失败（{}），继续尝试下一个", host, port, e.getMessage());
            }
        }
        throw new IllegalStateException("所有 MySQL 候选地址均不可用: " + String.join(",", candidates));
    }

    private String buildJdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                + "&connectTimeout=2000&socketTimeout=10000"
                + "&useCursorFetch=false&rewriteBatchedStatements=true";
    }
}
