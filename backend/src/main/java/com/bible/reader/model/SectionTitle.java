package com.bible.reader.model;

import lombok.Data;

/**
 * 分段标题 / 诗篇卷标（对应 section_title 表）。
 */
@Data
public class SectionTitle {
    private Integer id;
    private Integer bookId;
    private Integer chapter;
    private Integer verse;
    private String kind;
    private String content;
}
