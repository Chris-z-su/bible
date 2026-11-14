package com.bible.reader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 精读圣经后端入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BibleReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BibleReaderApplication.class, args);
    }
}
