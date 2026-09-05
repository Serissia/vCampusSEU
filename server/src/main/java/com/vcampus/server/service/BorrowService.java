package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BorrowRecordVO;

import java.util.List;

/**
 * 图书借还业务接口。
 *
 * @author GGbongy
 */
public interface BorrowService {

    /**
     * 学生借书，返回具体业务状态码。
     */
    ResponseCode borrow(String studentId, String isbn);

    /**
     * 学生自助还书，返回具体业务状态码。
     */
    ResponseCode returnBook(String studentId, String isbn);

    /**
     * 学生自助续借，返回具体业务状态码。
     */
    ResponseCode renew(String studentId, String isbn);

    /**
     * 查询某学生的借阅记录。
     */
    List<BorrowRecordVO> listByStudent(String studentId);

    /**
     * 判断某用户是否存在未归还的借阅记录。
     */
    boolean hasActiveBorrows(String uid);
}
