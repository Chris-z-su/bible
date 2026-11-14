package com.bible.reader.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文本处理工具：引用归一化、LIKE 转义、高亮区间。
 */
public final class TextUtils {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private TextUtils() {
    }

    public static String nowIso() {
        // 截断到毫秒，前端 Date 解析与 ES date 都更稳妥
        return OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MILLIS).format(ISO_OFFSET);
    }

    public static String formatIso(OffsetDateTime time) {
        return time.format(ISO_OFFSET);
    }

    /**
     * 经文引用归一化：小写、去空白/句点、全角转半角、章/篇转冒号、删节、统一连字符、去尾部冒号。
     */
    public static String normalizeRef(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u3000' || Character.isWhitespace(c)) {
                continue;
            }
            if (c == '.') {
                continue;
            }
            if (c == '\uFF1A') {
                c = ':';
            } else if (c >= '\uFF10' && c <= '\uFF19') {
                c = (char) ('0' + (c - '\uFF10'));
            } else if (c == '\u7AE0' || c == '\u7BC7') {
                c = ':';
            } else if (c == '\u8282') {
                continue;
            } else if (c == '\u2014' || c == '~' || c == '\u2013' || c == '\uFF0D') {
                c = '-';
            }
            sb.append(c);
        }
        String normalized = sb.toString().toLowerCase(Locale.ROOT);
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == ':') {
            end--;
        }
        return normalized.substring(0, end);
    }

    /**
     * 转义 LIKE 通配符，配合 SQL 中的 ESCAPE '\\' 使用。
     */
    public static String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static List<String> splitTerms(String q) {
        if (q == null) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : q.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                terms.add(part);
            }
        }
        return new ArrayList<>(terms);
    }

    /**
     * 计算 [start, end) 高亮区间，多个关键词区间合并去重。
     */
    public static List<int[]> highlightRanges(String text, List<String> terms) {
        List<int[]> ranges = new ArrayList<>();
        if (text == null || text.isEmpty() || terms == null || terms.isEmpty()) {
            return ranges;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (term == null || term.isEmpty()) {
                continue;
            }
            String needle = term.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from <= lower.length() - needle.length()) {
                int index = lower.indexOf(needle, from);
                if (index < 0) {
                    break;
                }
                ranges.add(new int[]{index, index + needle.length()});
                from = index + Math.max(1, needle.length());
            }
        }
        ranges.sort(Comparator.comparingInt(r -> r[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new int[]{range[0], range[1]});
            }
        }
        return merged;
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 解析日志检索时间：支持 ISO-8601、yyyy-MM-dd（按 Asia/Shanghai 解释）。
     * 纯日期作为 to 时取当天 23:59:59.999。
     */
    public static OffsetDateTime parseDateTime(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate date = LocalDate.parse(value);
            LocalTime time = endOfDay ? LocalTime.of(23, 59, 59, 999_000_000) : LocalTime.MIDNIGHT;
            return date.atTime(time).atZone(ZONE).toOffsetDateTime();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDateTime.parse(value).atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }
        try {
            return Instant.parse(value).atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            throw new DateTimeParseException("无法解析时间: " + raw, value, 0);
        }
    }
}
