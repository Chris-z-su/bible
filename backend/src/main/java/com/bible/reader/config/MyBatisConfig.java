package com.bible.reader.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描。XML 位于 classpath:mapper/*.xml。
 */
@Configuration
@MapperScan("com.bible.reader.mapper")
public class MyBatisConfig {
}
