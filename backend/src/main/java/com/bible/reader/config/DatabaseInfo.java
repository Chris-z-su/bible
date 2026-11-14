package com.bible.reader.config;

/**
 * 记录实际选中的 MySQL 地址，供启动 banner 与排查使用。
 */
public record DatabaseInfo(String url, String host, int port, String database) {

    public static DatabaseInfo fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new DatabaseInfo("", "unknown", 0, "");
        }
        String rest = jdbcUrl;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int query = rest.indexOf('?');
        if (query >= 0) {
            rest = rest.substring(0, query);
        }
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        String database = slash >= 0 ? rest.substring(slash + 1) : "";
        String host = hostPort;
        int port = 3306;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                port = 3306;
            }
        }
        return new DatabaseInfo(jdbcUrl, host, port, database);
    }

    public String display() {
        return host + ":" + port + "/" + database;
    }
}
