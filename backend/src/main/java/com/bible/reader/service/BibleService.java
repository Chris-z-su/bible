package com.bible.reader.service;

import com.bible.reader.common.BizException;
import com.bible.reader.dto.BookChaptersDto;
import com.bible.reader.dto.BookDto;
import com.bible.reader.dto.ChapterDetailDto;
import com.bible.reader.dto.ChapterDto;
import com.bible.reader.dto.ChapterRefDto;
import com.bible.reader.dto.StatsDto;
import com.bible.reader.dto.TitleDto;
import com.bible.reader.dto.VerseDto;
import com.bible.reader.mapper.BookMapper;
import com.bible.reader.mapper.ChapterMapper;
import com.bible.reader.mapper.SectionTitleMapper;
import com.bible.reader.mapper.VerseMapper;
import com.bible.reader.model.Book;
import com.bible.reader.model.Chapter;
import com.bible.reader.model.SectionTitle;
import com.bible.reader.model.Verse;
import com.bible.reader.util.RedisCache;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 圣经正文读取。books/chapter/stats 使用 Redis 缓存，异常自动回落数据库。
 */
@Slf4j
@Service
public class BibleService {

    private static final Duration BOOKS_TTL = Duration.ofHours(1);
    private static final Duration CHAPTER_TTL = Duration.ofMinutes(30);
    private static final Duration STATS_TTL = Duration.ofMinutes(10);
    private static final String KEY_BOOKS = "books";
    private static final String KEY_STATS = "stats";
    private static final String KEY_CHAPTER_PREFIX = "chapter:";

    private static final TypeReference<List<BookDto>> BOOKS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ChapterDetailDto> CHAPTER_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<StatsDto> STATS_TYPE = new TypeReference<>() {
    };

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final VerseMapper verseMapper;
    private final SectionTitleMapper sectionTitleMapper;
    private final RedisCache redisCache;

    public BibleService(BookMapper bookMapper,
                        ChapterMapper chapterMapper,
                        VerseMapper verseMapper,
                        SectionTitleMapper sectionTitleMapper,
                        RedisCache redisCache) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.verseMapper = verseMapper;
        this.sectionTitleMapper = sectionTitleMapper;
        this.redisCache = redisCache;
    }

    public List<BookDto> listBooks() {
        List<BookDto> cached = redisCache.get(KEY_BOOKS, BOOKS_TYPE);
        if (cached != null) {
            return cached;
        }
        List<Book> books = bookMapper.findAllOrdered();
        List<BookDto> result = new ArrayList<>(books.size());
        for (Book book : books) {
            result.add(toDto(book));
        }
        redisCache.set(KEY_BOOKS, result, BOOKS_TTL);
        return result;
    }

    public BookDto getBook(int bookId) {
        return listBooks().stream()
                .filter(book -> Objects.equals(book.getId(), bookId))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("书卷不存在"));
    }

    public BookChaptersDto getBookChapters(int bookId) {
        BookDto book = getBook(bookId);
        List<Chapter> chapters = chapterMapper.findByBookId(bookId);
        List<ChapterDto> items = new ArrayList<>(chapters.size());
        for (Chapter chapter : chapters) {
            items.add(new ChapterDto(chapter.getChapter(), chapter.getVerseCount(), chapter.getTitleCount()));
        }
        return new BookChaptersDto(book.getId(), book.getName(), book.getChapterCount(), items);
    }

    public ChapterDetailDto getChapter(int bookId, int chapter) {
        String key = KEY_CHAPTER_PREFIX + bookId + ":" + chapter;
        ChapterDetailDto cached = redisCache.get(key, CHAPTER_TYPE);
        if (cached != null) {
            return cached;
        }

        BookDto book = getBook(bookId);
        Chapter chapterRow = chapterMapper.findOne(bookId, chapter);
        if (chapterRow == null) {
            throw BizException.notFound("章节不存在");
        }

        List<Verse> verseRows = verseMapper.findByChapter(bookId, chapter);
        List<VerseDto> verses = new ArrayList<>(verseRows.size());
        for (Verse verse : verseRows) {
            verses.add(new VerseDto(verse.getVerse(), verse.getContent()));
        }

        List<SectionTitle> titleRows = sectionTitleMapper.findByChapter(bookId, chapter);
        List<TitleDto> titles = new ArrayList<>(titleRows.size());
        for (SectionTitle title : titleRows) {
            titles.add(new TitleDto(title.getVerse(), title.getKind(), title.getContent()));
        }

        List<BookDto> books = listBooks();
        ChapterRefDto prev = previousChapter(books, book, chapter);
        ChapterRefDto next = nextChapter(books, book, chapter);

        ChapterDetailDto detail = new ChapterDetailDto(book, chapter, chapterRow.getVerseCount(),
                verses, titles, prev, next);
        redisCache.set(key, detail, CHAPTER_TTL);
        return detail;
    }

    public StatsDto getStats() {
        StatsDto cached = redisCache.get(KEY_STATS, STATS_TYPE);
        if (cached != null) {
            return cached;
        }
        StatsDto stats = new StatsDto(
                bookMapper.countAll(),
                chapterMapper.countAll(),
                verseMapper.countAll(),
                sectionTitleMapper.countAll(),
                bookMapper.countByTestament("OT"),
                bookMapper.countByTestament("NT"));
        redisCache.set(KEY_STATS, stats, STATS_TTL);
        return stats;
    }

    private ChapterRefDto previousChapter(List<BookDto> books, BookDto current, int chapter) {
        if (chapter > 1) {
            return ref(current, chapter - 1);
        }
        int index = indexOf(books, current.getId());
        if (index > 0) {
            BookDto previousBook = books.get(index - 1);
            return ref(previousBook, previousBook.getChapterCount());
        }
        return null;
    }

    private ChapterRefDto nextChapter(List<BookDto> books, BookDto current, int chapter) {
        if (current.getChapterCount() != null && chapter < current.getChapterCount()) {
            return ref(current, chapter + 1);
        }
        int index = indexOf(books, current.getId());
        if (index >= 0 && index < books.size() - 1) {
            BookDto nextBook = books.get(index + 1);
            return ref(nextBook, 1);
        }
        return null;
    }

    private int indexOf(List<BookDto> books, Integer bookId) {
        for (int i = 0; i < books.size(); i++) {
            if (Objects.equals(books.get(i).getId(), bookId)) {
                return i;
            }
        }
        return -1;
    }

    private ChapterRefDto ref(BookDto book, int chapter) {
        return new ChapterRefDto(book.getId(), book.getName(), book.getShortName(), chapter);
    }

    private BookDto toDto(Book book) {
        return new BookDto(book.getId(), book.getName(), book.getShortName(),
                book.getEnglishName(), book.getEnglishAbbr(), book.getTestament(),
                book.getCategory(), book.getChapterCount(), book.getVerseCount());
    }
}
