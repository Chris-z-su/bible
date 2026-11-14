package com.bible.reader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {
    private String q;
    private Integer page;
    private Integer size;
    private Long total;
    private Long took;
    private List<String> terms;

    /** 普通关键词搜索时显式返回 null。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ReferenceDto reference;

    private List<SearchItemDto> items;
}
