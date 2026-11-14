package com.bible.reader.mapper;

import com.bible.reader.model.SectionTitle;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SectionTitleMapper {

    List<SectionTitle> findByChapter(@Param("bookId") int bookId, @Param("chapter") int chapter);

    long countAll();
}
