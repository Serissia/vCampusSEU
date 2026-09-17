package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.BookPageVO;
import com.vcampus.common.vo.BookQueryVO;
import com.vcampus.common.vo.BookVO;
import com.vcampus.server.dao.BookDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图书 JDBC 实现。
 *
 * @author GGbongy
 */
public class BookDaoImpl implements BookDao {

    private static final String COLUMNS = "isbn, title, author, publisher, category, location, resource_file, type, total_num, current_num";

    /**
     * 按 ISBN、书名或作者进行模糊查询，关键字为空时返回全部馆藏。
     */
    @Override
    public List<BookVO> queryBooks(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ?";
        List<BookVO> result = new ArrayList<BookVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + keyword + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapBook(rs));
                }
            }
        }
        return result;
    }

    /**
     * 按关键字和分类分页查询图书，分类为空时不过滤分类。
     */
    @Override
    public BookPageVO queryBooks(BookQueryVO query) throws SQLException {
        String keyword = normalize(query == null ? null : query.getKeyword());
        String category = normalize(query == null ? null : query.getCategory());
        int page = Math.max(1, query == null ? 1 : query.getPage());
        int pageSize = Math.max(1, Math.min(100, query == null ? 20 : query.getPageSize()));

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<Object>();
        if (!keyword.isEmpty()) {
            String like = "%" + keyword + "%";
            where.append("AND (isbn LIKE ? OR title LIKE ? OR author LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!category.isEmpty()) {
            where.append("AND category = ? ");
            params.add(category);
        }

        int total = countBooks(where.toString(), params);
        int offset = (page - 1) * pageSize;
        String sql = "SELECT " + COLUMNS + " FROM tbl_book" + where
                + "ORDER BY title ASC, isbn ASC LIMIT ? OFFSET ?";
        List<BookVO> items = new ArrayList<BookVO>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.setInt(params.size() + 1, pageSize);
            ps.setInt(params.size() + 2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapBook(rs));
                }
            }
        }

        BookPageVO result = new BookPageVO();
        result.setItems(items);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setHasMore(offset + items.size() < total);
        return result;
    }

    /**
     * 按 ISBN 精确查询图书。
     */
    @Override
    public BookVO findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_book WHERE isbn = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBook(rs);
                }
            }
        }
        return null;
    }

    /**
     * 新增图书，馆藏总数与可借余量一致。
     */
    @Override
    public boolean insertBook(BookVO book) throws SQLException {
        String sql = "INSERT INTO tbl_book(isbn, title, author, publisher, category, location, resource_file, type, total_num, current_num) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getPublisher());
            ps.setString(5, book.getCategory());
            ps.setString(6, book.getLocation());
            ps.setString(7, book.getResourceFile());
            ps.setString(8, book.getType());
            ps.setInt(9, book.getTotalNum());
            ps.setInt(10, book.getCurrentNum());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新图书信息（ISBN 作为业务主键不可变更）。
     */
    @Override
    public boolean updateBook(BookVO book) throws SQLException {
        String sql = "UPDATE tbl_book SET title=?, author=?, publisher=?, category=?, location=?, resource_file=?, type=?, total_num=? "
                + "WHERE isbn=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getPublisher());
            ps.setString(4, book.getCategory());
            ps.setString(5, book.getLocation());
            ps.setString(6, book.getResourceFile());
            ps.setString(7, book.getType());
            ps.setInt(8, book.getTotalNum());
            ps.setString(9, book.getIsbn());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 删除图书。
     */
    @Override
    public boolean deleteBook(String isbn) throws SQLException {
        String sql = "DELETE FROM tbl_book WHERE isbn = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 通过 SQL 原子减少余量，避免并发借阅导致余量变负。
     */
    @Override
    public boolean decreaseCurrentNum(String isbn) throws SQLException {
        String sql = "UPDATE tbl_book SET current_num = current_num - 1 WHERE isbn = ? AND current_num > 0";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 通过 SQL 原子增加余量，归还时不超过馆藏总数。
     */
    @Override
    public boolean increaseCurrentNum(String isbn) throws SQLException {
        String sql = "UPDATE tbl_book SET current_num = current_num + 1 WHERE isbn = ? AND current_num < total_num";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将 ResultSet 当前行转换为 BookVO。
     */
    private BookVO mapBook(ResultSet rs) throws SQLException {
        BookVO book = new BookVO();
        book.setIsbn(rs.getString("isbn"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setPublisher(rs.getString("publisher"));
        book.setCategory(rs.getString("category"));
        book.setLocation(rs.getString("location"));
        book.setResourceFile(rs.getString("resource_file"));
        book.setType(rs.getString("type"));
        book.setTotalNum(rs.getInt("total_num"));
        book.setCurrentNum(rs.getInt("current_num"));
        return book;
    }

    /**
     * 统计满足相同筛选条件的总条数，供分页结果判断是否还有下一页。
     */
    private int countBooks(String where, List<Object> params) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_book" + where;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String normalize(String value) {
        if (value == null || "null".equals(value)) {
            return "";
        }
        return value.trim();
    }
}
