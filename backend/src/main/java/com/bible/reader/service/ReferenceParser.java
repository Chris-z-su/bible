package com.bible.reader.service;

import com.bible.reader.dto.ReferenceDto;
import com.bible.reader.mapper.BookMapper;
import com.bible.reader.mapper.ChapterMapper;
import com.bible.reader.model.Book;
import com.bible.reader.model.Chapter;
import com.bible.reader.util.TextUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 经文引用解析器。启动时加载书卷、别名与各章节数，避免每次请求查库。
 * 别名必须“最长优先”，否则“约”会误匹配“约拿书”。
 */
@Slf4j
@Component
public class ReferenceParser {

    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("^(\\d{1,3})(?::(\\d{1,3})(?:-(\\d{1,3}))?)?$");
    private static final TypeReference<List<String>> ALIAS_TYPE = new TypeReference<>() {
    };

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final ObjectMapper objectMapper;

    private volatile boolean loaded;
    private volatile List<Book> books = List.of();
    private volatile Map<Integer, Book> booksById = Map.of();
    private volatile Map<String, Book> aliasIndex = Map.of();
    private volatile List<Map.Entry<String, Book>> aliasesByLength = List.of();
    /** bookId -> 下标为 chapter 的数组，值为该章节数。 */
    private volatile Map<Integer, int[]> chapterVerseCounts = Map.of();

    public ReferenceParser(BookMapper bookMapper, ChapterMapper chapterMapper, ObjectMapper objectMapper) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.objectMapper = objectMapper;
    }

    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            List<Book> allBooks = bookMapper.findAllOrdered();
            Map<Integer, Book> byId = new HashMap<>();
            Map<String, Book> aliases = new HashMap<>();
            for (Book book : allBooks) {
                byId.put(book.getId(), book);
                for (String alias : parseAliases(book)) {
                    String normalized = TextUtils.normalizeRef(alias);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    // 同一别名理论上可能对应多卷，取 id 最小者
                    aliases.merge(normalized, book,
                            (existing, candidate) -> existing.getId() <= candidate.getId() ? existing : candidate);
                }
            }
            List<Map.Entry<String, Book>> sortedAliases = new ArrayList<>(aliases.entrySet());
            sortedAliases.sort(Comparator
                    .comparingInt((Map.Entry<String, Book> e) -> e.getKey().length()).reversed()
                    .thenComparing(Map.Entry::getKey));

            Map<Integer, int[]> verses = new HashMap<>();
            for (Book book : allBooks) {
                int size = Math.max(book.getChapterCount() == null ? 1 : book.getChapterCount(), 1) + 1;
                verses.put(book.getId(), new int[size]);
            }
            for (Chapter chapter : chapterMapper.findAll()) {
                int[] counts = verses.get(chapter.getBookId());
                if (counts != null && chapter.getChapter() != null
                        && chapter.getChapter() >= 1 && chapter.getChapter() < counts.length
                        && chapter.getVerseCount() != null) {
                    counts[chapter.getChapter()] = chapter.getVerseCount();
                }
            }

            this.books = allBooks;
            this.booksById = byId;
            this.aliasIndex = aliases;
            this.aliasesByLength = sortedAliases;
            this.chapterVerseCounts = verses;
            this.loaded = true;
            log.info("经文引用解析器已加载：{} 卷、{} 个别名、{} 章", allBooks.size(), aliases.size(), chapterMapper.findAll().size());
        }
    }

    /**
     * @param raw           原始引用文本
     * @param contextBookId q 只有“章:节”时使用的书卷上下文，可为 null
     */
    public Optional<ReferenceDto> parse(String raw, Integer contextBookId) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        String normalized = TextUtils.normalizeRef(raw);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Book book = null;
        String remainder = null;
        for (Map.Entry<String, Book> entry : aliasesByLength) {
            String alias = entry.getKey();
            if (normalized.length() <= alias.length() || !normalized.startsWith(alias)) {
                continue;
            }
            int next = alias.length();
            char first = normalized.charAt(next);
            boolean startsWithNumber = Character.isDigit(first)
                    || (first == ':' && next + 1 < normalized.length()
                    && Character.isDigit(normalized.charAt(next + 1)));
            if (startsWithNumber) {
                book = entry.getValue();
                remainder = normalized.substring(next);
                break;
            }
        }

        if (book == null) {
            if (contextBookId != null && Character.isDigit(normalized.charAt(0))) {
                book = booksById.get(contextBookId);
                remainder = normalized;
            }
            if (book == null) {
                return Optional.empty();
            }
        }

        // “诗篇23”归一化后是“诗:23”，别名“诗”之后的剩余部分可能以冒号开头
        int skip = 0;
        while (skip < remainder.length() && remainder.charAt(skip) == ':') {
            skip++;
        }
        remainder = remainder.substring(skip);
        if (remainder.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = REFERENCE_PATTERN.matcher(remainder);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return build(book, matcher);
    }

    private Optional<ReferenceDto> build(Book book, Matcher matcher) {
        int chapter = Integer.parseInt(matcher.group(1));
        Integer chapterCount = book.getChapterCount();
        if (chapterCount == null || chapter < 1 || chapter > chapterCount) {
            return Optional.empty();
        }
        int[] counts = chapterVerseCounts.get(book.getId());
        if (counts == null || chapter >= counts.length) {
            return Optional.empty();
        }
        int verseCount = counts[chapter];
        if (verseCount <= 0) {
            return Optional.empty();
        }

        int verseStart;
        int verseEnd;
        boolean chapterOnly = matcher.group(2) == null;
        if (chapterOnly) {
            verseStart = 1;
            verseEnd = verseCount;
        } else {
            verseStart = Integer.parseInt(matcher.group(2));
            int rawEnd = matcher.group(3) == null ? verseStart : Integer.parseInt(matcher.group(3));
            if (verseStart < 1 || rawEnd < verseStart || verseStart > verseCount) {
                return Optional.empty();
            }
            // 结束节超过该章最大节数时截断
            verseEnd = Math.min(rawEnd, verseCount);
        }

        String display;
        if (chapterOnly) {
            display = book.getName() + " " + chapter;
        } else if (verseStart == verseEnd) {
            display = book.getName() + " " + chapter + ":" + verseStart;
        } else {
            display = book.getName() + " " + chapter + ":" + verseStart + "-" + verseEnd;
        }
        return Optional.of(new ReferenceDto(book.getId(), book.getName(), book.getShortName(),
                chapter, verseStart, verseEnd, display));
    }

    private List<String> parseAliases(Book book) {
        List<String> result = new ArrayList<>();
        if (book.getAliases() != null && !book.getAliases().isBlank()) {
            try {
                List<String> parsed = objectMapper.readValue(book.getAliases(), ALIAS_TYPE);
                if (parsed != null) {
                    result.addAll(parsed);
                }
            } catch (Exception e) {
                log.warn("书卷 {} 的 aliases 不是合法 JSON，使用基础别名", book.getId());
            }
        }
        if (result.isEmpty()) {
            result.add(book.getName());
            result.add(book.getShortName());
            result.add(book.getEnglishName());
            result.add(book.getEnglishAbbr());
        }
        return result;
    }

    public List<Book> loadedBooks() {
        ensureLoaded();
        return books;
    }
}
