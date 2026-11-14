package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookDto {
    private Integer id;
    private String name;
    private String shortName;
    private String englishName;
    private String englishAbbr;
    private String testament;
    private String category;
    private Integer chapterCount;
    private Integer verseCount;
}
