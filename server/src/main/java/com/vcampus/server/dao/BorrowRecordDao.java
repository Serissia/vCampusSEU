package com.vcampus.server.dao;

import com.vcampus.common.vo.BorrowRecordVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 图书借阅记录数据访问接口。
 *
 * @author GGbongy
 */
public interface BorrowRecordDao {

    /**
     * 写入一条借阅记录。
     */
    boolean insertBorrow(BorrowRecordVO record) throws SQLException;

    /**
     * 将指定借阅记录标记为已归还。
     */
    boolean markReturned(String studentId, String isbn, String returnDate) throws SQLException;

    /**
     * 统计某学生当前未归还的借阅数量。
     */
    int countActiveBorrows(String studentId) throws SQLException;

    /**
     * 判断某学生是否已借阅该书且未归还。
     */
    boolean hasActiveBorrow(String studentId, String isbn) throws SQLException;

    /**
     * 查询某学生的全部借阅记录（含已归还）。
     */
    List<BorrowRecordVO> listByStudent(String studentId) throws SQLException;

    /**
     * 查询某学生某本书当前未归还的借阅记录。
     */
    BorrowRecordVO findActiveBorrow(String studentId, String isbn) throws SQLException;

    /**
     * 续借：更新应还日期并将续借次数加一。
     */
    boolean renew(String studentId, String isbn, String newDueDate) throws SQLException;
}
