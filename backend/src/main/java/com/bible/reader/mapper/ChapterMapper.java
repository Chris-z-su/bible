package com.bible.reader.mapper;

import com.bible.reader.model.Chapter;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChapterMapper {

    List<Chapter> findByBookId(@Param("bookId") int bookId);

    Chapter findOne(@Param("bookId") int bookId, @Param("chapter") int chapter);

    List<Chapter> findAll();

    long countAll();
}
