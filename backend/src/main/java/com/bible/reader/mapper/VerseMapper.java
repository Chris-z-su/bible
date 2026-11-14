package com.bible.reader.mapper;

import com.bible.reader.model.Verse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VerseMapper {

    List<Verse> findByChapter(@Param("bookId") int bookId, @Param("chapter") int chapter);

    List<Verse> findRange(@Param("bookId") int bookId,
                          @Param("chapter") int chapter,
                          @Param("startVerse") int startVerse,
                          @Param("endVerse") int endVerse,
                          @Param("limit") int limit);

    long countSearch(@Param("terms") List<String> terms, @Param("bookId") Integer bookId);

    List<Verse> search(@Param("terms") List<String> terms,
                       @Param("bookId") Integer bookId,
                       @Param("size") int size,
                       @Param("offset") long offset);

    long countAll();
}
