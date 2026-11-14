package com.bible.reader.controller;

import com.bible.reader.common.ApiResponse;
import com.bible.reader.common.BizException;
import com.bible.reader.dto.ReferenceDto;
import com.bible.reader.dto.SearchResultDto;
import com.bible.reader.service.ReferenceParser;
import com.bible.reader.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;
    private final ReferenceParser referenceParser;

    public SearchController(SearchService searchService, ReferenceParser referenceParser) {
        this.searchService = searchService;
        this.referenceParser = referenceParser;
    }

    @GetMapping("/search")
    public ApiResponse<SearchResultDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer bookId) {
        return ApiResponse.ok(searchService.search(q, page, size, bookId));
    }

    @GetMapping("/reference/parse")
    public ApiResponse<ReferenceDto> parseReference(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer bookId) {
        if (q == null || q.isBlank()) {
            throw BizException.badRequest("缺少参数: q");
        }
        if (bookId != null && bookId < 1) {
            throw BizException.badRequest("bookId 必须为正整数");
        }
        ReferenceDto reference = referenceParser.parse(q, bookId)
                .orElseThrow(() -> BizException.badRequest("无法识别的经文引用"));
        return ApiResponse.ok(reference);
    }
}
