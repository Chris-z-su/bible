package com.bible.reader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthDto {
    private String status;
    private Boolean mysql;
    private Boolean redis;
    private Boolean elasticsearch;
    private String version;
    private String time;
}
