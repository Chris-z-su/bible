package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResultDto {
    private Long total;
    private Integer page;
    private Integer size;
    private Long took;
    private Boolean degraded;
    private List<LogHitDto> items;
    private Stats stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Map<String, Long> levels;
        private Map<String, Long> events;
    }
}
