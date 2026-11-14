package com.bible.reader.service;

import com.bible.reader.common.BizException;
import com.bible.reader.dto.BookDto;
import com.bible.reader.dto.ReferenceDto;
import com.bible.reader.dto.SearchItemDto;
import com.bible.reader.dto.SearchResultDto;
import com.bible.reader.mapper.VerseMapper;
import com.bible.reader.model.Verse;
import com.bible.reader.util.TextUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索：先尝试引用解析，失败则多关键词 LIKE（AND）。
 */
@Service
public class SearchService {

    private static final int MAX_REFERENCE_VERSES = 200;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 50;

    private final VerseMapper verseMapper;
    private final BibleService bibleService;
    private final ReferenceParser referenceParser;

    public SearchService(VerseMapper verseMapper, BibleService bibleService, ReferenceParser referenceParser) {
        this.verseMapper = verseMapper;
        this.bibleService = bibleService;
        this.referenceParser = referenceParser;
    }

    public SearchResultDto search(String rawQ, Integer page, Integer size, Integer bookId) {
        long startedAt = System.nanoTime();
        String q = rawQ == null ? "" : rawQ.trim();
        if (q.isEmpty() || q.length() > MAX_QUERY_LENGTH) {
            throw BizException.badRequest("搜索关键词长度需在 1~100 字符之间");
        }
        int safePage = page == null ? 1 : page;
        int safeSize = size == null ? 20 : size;
        if (safePage < 1) {
            throw BizException.badRequest("page 必须大于等于 1");
        }
        if (safeSize < 1 || safeSize > MAX_PAGE_SIZE) {
            throw BizException.badRequest("size 必须在 1~50 之间");
        }
        if (bookId != null && bookId < 1) {
            throw BizException.badRequest("bookId 必须为正整数");
        }

        ReferenceDto reference = referenceParser.parse(q, bookId).orElse(null);
        if (reference != null) {
            return searchByReference(q, reference, startedAt);
        }

        List<String> terms = TextUtils.splitTerms(q);
        if (terms.isEmpty()) {
            throw BizException.badRequest("搜索关键词不能为空");
        }
        List<String> escapedTerms = terms.stream().map(TextUtils::escapeLike).toList();

        long total = verseMapper.countSearch(escapedTerms, bookId);
        long offset = (long) (safePage - 1) * safeSize;
        List<Verse> verses = offset >= total
                ? List.of()
                : verseMapper.search(escapedTerms, bookId, safeSize, offset);

        Map<Integer, BookDto> bookMap = new HashMap<>();
        for (BookDto book : bibleService.listBooks()) {
            bookMap.put(book.getId(), book);
        }

        List<SearchItemDto> items = new ArrayList<>(verses.size());
        for (Verse verse : verses) {
            BookDto book = bookMap.get(verse.getBookId());
            String bookName = book == null ? "" : book.getName();
            String shortName = book == null ? "" : book.getShortName();
            String ref = bookName + " " + verse.getChapter() + ":" + verse.getVerse();
            items.add(new SearchItemDto(verse.getBookId(), bookName, shortName,
                    verse.getChapter(), verse.getVerse(), ref, verse.getContent(),
                    TextUtils.highlightRanges(verse.getContent(), terms)));
        }

        SearchResultDto result = new SearchResultDto();
        result.setQ(q);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setTotal(total);
        result.setTook(elapsedMs(startedAt));
        result.setTerms(terms);
        result.setReference(null);
        result.setItems(items);
        return result;
    }

    private SearchResultDto searchByReference(String q, ReferenceDto reference, long startedAt) {
        List<Verse> verses = verseMapper.findRange(reference.getBookId(), reference.getChapter(),
                reference.getVerseStart(), reference.getVerseEnd(), MAX_REFERENCE_VERSES);
        List<SearchItemDto> items = new ArrayList<>(verses.size());
        for (Verse verse : verses) {
            String ref = reference.getBookName() + " " + verse.getChapter() + ":" + verse.getVerse();
            items.add(new SearchItemDto(verse.getBookId(), reference.getBookName(),
                    reference.getBookShortName(), verse.getChapter(), verse.getVerse(),
                    ref, verse.getContent(), List.of()));
        }
        SearchResultDto result = new SearchResultDto();
        result.setQ(q);
        // 引用搜索不分页，size 固定 200
        result.setPage(1);
        result.setSize(MAX_REFERENCE_VERSES);
        result.setTotal((long) items.size());
        result.setTook(elapsedMs(startedAt));
        result.setTerms(List.of());
        result.setReference(reference);
        result.setItems(items);
        return result;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
