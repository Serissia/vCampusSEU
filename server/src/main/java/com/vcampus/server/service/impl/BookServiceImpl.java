package com.vcampus.server.service.impl;

import com.vcampus.common.vo.BookVO;
import com.vcampus.server.dao.BookDao;
import com.vcampus.server.dao.impl.BookDaoImpl;
import com.vcampus.server.service.BookService;

import java.sql.SQLException;
import java.util.List;

/**
 * 图书管理业务实现。
 *
 * @author GGbongy
 */
public class BookServiceImpl implements BookService {

    private final BookDao bookDao = new BookDaoImpl();

    /**
     * 查询图书，空关键字转为匹配全部。
     */
    @Override
    public List<BookVO> queryBooks(String keyword) {
        try {
            if (keyword == null || "null".equals(keyword)) {
                keyword = "";
            }
            return bookDao.queryBooks(keyword.trim());
        } catch (SQLException e) {
            throw new RuntimeException("查询图书失败", e);
        }
    }

    /**
     * 新增图书，新增时确保余量与总数一致。
     */
    @Override
    public boolean addBook(BookVO book) {
        try {
            if (book == null || book.getIsbn() == null || book.getIsbn().trim().length() == 0) {
                return false;
            }
            // 新增图书时未指定余量，默认与馆藏总数一致
            if (book.getTotalNum() <= 0) {
                book.setTotalNum(1);
            }
            if (book.getCurrentNum() <= 0) {
                book.setCurrentNum(book.getTotalNum());
            }
            return bookDao.insertBook(book);
        } catch (SQLException e) {
            throw new RuntimeException("新增图书失败", e);
        }
    }

    /**
     * 更新图书。
     */
    @Override
    public boolean updateBook(BookVO book) {
        try {
            return book != null && bookDao.updateBook(book);
        } catch (SQLException e) {
            throw new RuntimeException("更新图书失败", e);
        }
    }

    /**
     * 删除图书。
     */
    @Override
    public boolean deleteBook(String isbn) {
        try {
            return isbn != null && bookDao.deleteBook(isbn);
        } catch (SQLException e) {
            throw new RuntimeException("删除图书失败", e);
        }
    }
}
