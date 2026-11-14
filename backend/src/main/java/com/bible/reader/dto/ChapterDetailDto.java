package com.bible.reader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDetailDto {
    private BookDto book;
    private Integer chapter;
    private Integer verseCount;
    private List<VerseDto> verses;
    private List<TitleDto> titles;

    /** 首章/末章必须显式输出 null，前端据此隐藏按钮。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ChapterRefDto prev;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ChapterRefDto next;
}
