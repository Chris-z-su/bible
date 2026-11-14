package com.bible.reader.mapper;

import com.bible.reader.model.Book;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BookMapper {

    List<Book> findAllOrdered();

    Book findById(@Param("id") int id);

    long countAll();

    long countByTestament(@Param("testament") String testament);
}
