package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookChaptersDto {
    private Integer bookId;
    private String bookName;
    private Integer chapterCount;
    private List<ChapterDto> chapters;
}
