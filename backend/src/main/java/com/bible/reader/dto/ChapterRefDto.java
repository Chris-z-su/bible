package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterRefDto {
    private Integer bookId;
    private String bookName;
    private String bookShortName;
    private Integer chapter;
}
