package com.bible.reader.model;

import lombok.Data;

/**
 * 书卷（对应 book 表）。
 */
@Data
public class Book {
    private Integer id;
    private String name;
    private String shortName;
    private String englishName;
    private String englishAbbr;
    private String aliases;
    private String testament;
    private String category;
    private Integer chapterCount;
    private Integer verseCount;
    private Integer sortOrder;
}
