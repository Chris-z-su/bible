package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchItemDto {
    private Integer bookId;
    private String bookName;
    private String bookShortName;
    private Integer chapter;
    private Integer verse;
    private String ref;
    private String text;
    /** [start, end) UTF-16 下标区间。 */
    private List<int[]> highlights;
}
