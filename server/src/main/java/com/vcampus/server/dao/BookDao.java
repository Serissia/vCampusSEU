package com.vcampus.server.dao;

import com.vcampus.common.vo.BookVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 图书数据访问接口。
 *
 * @author GGbongy
 */
public interface BookDao {

    /**
     * 按 ISBN、书名或作者进行模糊查询。
     */
    List<BookVO> queryBooks(String keyword) throws SQLException;

    /**
     * 按 ISBN 精确查询图书。
     */
    BookVO findByIsbn(String isbn) throws SQLException;

    /**
     * 新增图书。
     */
    boolean insertBook(BookVO book) throws SQLException;

    /**
     * 更新图书信息。
     */
    boolean updateBook(BookVO book) throws SQLException;

    /**
     * 删除图书。
     */
    boolean deleteBook(String isbn) throws SQLException;

    /**
     * 原子减少可借余量（借出时调用，余量为 0 时拒绝）。
     */
    boolean decreaseCurrentNum(String isbn) throws SQLException;

    /**
     * 原子增加可借余量（归还时调用，不超过馆藏总数）。
     */
    boolean increaseCurrentNum(String isbn) throws SQLException;
}
