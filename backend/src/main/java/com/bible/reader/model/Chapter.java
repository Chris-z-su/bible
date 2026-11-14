package com.bible.reader.model;

import lombok.Data;

/**
 * 章（对应 chapter 表）。
 */
@Data
public class Chapter {
    private Integer bookId;
    private Integer chapter;
    private Integer verseCount;
    private Integer titleCount;
}
