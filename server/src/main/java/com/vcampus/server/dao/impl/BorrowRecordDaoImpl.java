package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.BorrowRecordVO;
import com.vcampus.server.dao.BorrowRecordDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图书借阅记录 JDBC 实现。
 *
 * @author GGbongy
 */
public class BorrowRecordDaoImpl implements BorrowRecordDao {

    /**
     * 写入一条借阅记录。
     */
    @Override
    public boolean insertBorrow(BorrowRecordVO record) throws SQLException {
        String sql = "INSERT INTO tbl_borrow_record(student_id, isbn, borrow_date, due_date, status) "
                + "VALUES (?, ?, ?, ?, 'BORROWED')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getStudentId());
            ps.setString(2, record.getIsbn());
            ps.setString(3, record.getBorrowDate());
            ps.setString(4, record.getDueDate());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将指定未归还记录标记为已归还。
     */
    @Override
    public boolean markReturned(String studentId, String isbn, String returnDate) throws SQLException {
        String sql = "UPDATE tbl_borrow_record SET return_date = ?, status = 'RETURNED' "
                + "WHERE student_id = ? AND isbn = ? AND status = 'BORROWED'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, returnDate);
            ps.setString(2, studentId);
            ps.setString(3, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 统计未归还借阅数量。
     */
    @Override
    public int countActiveBorrows(String studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_borrow_record WHERE student_id = ? AND status = 'BORROWED'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 判断是否已借阅该书且未归还。
     */
    @Override
    public boolean hasActiveBorrow(String studentId, String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_borrow_record "
                + "WHERE student_id = ? AND isbn = ? AND status = 'BORROWED'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 联表查询某学生的借阅记录（含图书名称与作者）。
     */
    @Override
    public List<BorrowRecordVO> listByStudent(String studentId) throws SQLException {
        String sql = "SELECT r.id, r.student_id, r.isbn, b.title, b.author, "
                + "r.borrow_date, r.due_date, r.return_date, r.status, r.renew_count "
                + "FROM tbl_borrow_record r JOIN tbl_book b ON r.isbn = b.isbn "
                + "WHERE r.student_id = ? ORDER BY r.id DESC";
        List<BorrowRecordVO> result = new ArrayList<BorrowRecordVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRecord(rs));
                }
            }
        }
        return result;
    }

    /**
     * 查询某学生某本书当前未归还的借阅记录。
     */
    @Override
    public BorrowRecordVO findActiveBorrow(String studentId, String isbn) throws SQLException {
        String sql = "SELECT r.id, r.student_id, r.isbn, b.title, b.author, "
                + "r.borrow_date, r.due_date, r.return_date, r.status, r.renew_count "
                + "FROM tbl_borrow_record r JOIN tbl_book b ON r.isbn = b.isbn "
                + "WHERE r.student_id = ? AND r.isbn = ? AND r.status = 'BORROWED'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRecord(rs);
                }
            }
        }
        return null;
    }

    /**
     * 续借：更新应还日期并将续借次数加一。
     */
    @Override
    public boolean renew(String studentId, String isbn, String newDueDate) throws SQLException {
        String sql = "UPDATE tbl_borrow_record SET due_date = ?, renew_count = renew_count + 1 "
                + "WHERE student_id = ? AND isbn = ? AND status = 'BORROWED'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newDueDate);
            ps.setString(2, studentId);
            ps.setString(3, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将 ResultSet 当前行转换为 BorrowRecordVO。
     */
    private BorrowRecordVO mapRecord(ResultSet rs) throws SQLException {
        BorrowRecordVO record = new BorrowRecordVO();
        record.setId(rs.getInt("id"));
        record.setStudentId(rs.getString("student_id"));
        record.setIsbn(rs.getString("isbn"));
        record.setTitle(rs.getString("title"));
        record.setAuthor(rs.getString("author"));
        record.setBorrowDate(rs.getString("borrow_date"));
        record.setDueDate(rs.getString("due_date"));
        record.setReturnDate(rs.getString("return_date"));
        record.setStatus(rs.getString("status"));
        record.setRenewCount(rs.getInt("renew_count"));
        return record;
    }
}
