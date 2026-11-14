package com.bible.reader.model;

import lombok.Data;

/**
 * 经文（对应 verse 表）。
 */
@Data
public class Verse {
    private Integer id;
    private Integer bookId;
    private Integer chapter;
    private Integer verse;
    private String content;
}
