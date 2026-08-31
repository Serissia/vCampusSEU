package com.vcampus.server.service;

import com.vcampus.common.vo.BookVO;

import java.util.List;

/**
 * 图书管理业务接口。
 *
 * @author GGbongy
 */
public interface BookService {

    /**
     * 按关键字查询图书。
     */
    List<BookVO> queryBooks(String keyword);

    /**
     * 新增图书。
     */
    boolean addBook(BookVO book);

    /**
     * 更新图书。
     */
    boolean updateBook(BookVO book);

    /**
     * 删除图书。
     */
    boolean deleteBook(String isbn);
}
