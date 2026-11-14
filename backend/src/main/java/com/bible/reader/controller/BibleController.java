package com.bible.reader.controller;

import com.bible.reader.common.ApiResponse;
import com.bible.reader.dto.BookChaptersDto;
import com.bible.reader.dto.BookDto;
import com.bible.reader.dto.ChapterDetailDto;
import com.bible.reader.service.BibleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BibleController {

    private final BibleService bibleService;

    public BibleController(BibleService bibleService) {
        this.bibleService = bibleService;
    }

    @GetMapping("/books")
    public ApiResponse<List<BookDto>> books() {
        return ApiResponse.ok(bibleService.listBooks());
    }

    @GetMapping("/books/{bookId}/chapters")
    public ApiResponse<BookChaptersDto> bookChapters(@PathVariable int bookId) {
        return ApiResponse.ok(bibleService.getBookChapters(bookId));
    }

    @GetMapping("/chapters/{bookId}/{chapter}")
    public ApiResponse<ChapterDetailDto> chapter(@PathVariable int bookId, @PathVariable int chapter) {
        return ApiResponse.ok(bibleService.getChapter(bookId, chapter));
    }
}
