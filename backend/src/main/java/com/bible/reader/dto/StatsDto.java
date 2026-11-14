package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {
    private Long books;
    private Long chapters;
    private Long verses;
    private Long titles;
    private Long oldTestamentBooks;
    private Long newTestamentBooks;
}
