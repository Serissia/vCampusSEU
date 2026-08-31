package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.BorrowRecordVO;
import com.vcampus.server.dao.BookDao;
import com.vcampus.server.dao.BorrowRecordDao;
import com.vcampus.server.dao.impl.BookDaoImpl;
import com.vcampus.server.dao.impl.BorrowRecordDaoImpl;
import com.vcampus.server.service.BorrowService;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 图书借还业务实现。
 *
 * @author GGbongy
 */
public class BorrowServiceImpl implements BorrowService {

    /** 单次最多同时借阅本数 */
    private static final int MAX_ACTIVE_BORROWS = 5;
    /** 借阅期限（天） */
    private static final int BORROW_DAYS = 30;

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final BookDao bookDao = new BookDaoImpl();
    private final BorrowRecordDao borrowRecordDao = new BorrowRecordDaoImpl();

    /**
     * 借书：依次校验图书存在、余量、借阅上限、重复借阅，再写入借阅记录并扣减余量。
     */
    @Override
    public ResponseCode borrow(String studentId, String isbn) {
        try {
            BookVO book = bookDao.findByIsbn(isbn);
            if (book == null) {
                return ResponseCode.BOOK_NOT_FOUND;
            }
            if (book.getCurrentNum() <= 0) {
                return ResponseCode.BOOK_NO_STOCK;
            }
            if (borrowRecordDao.countActiveBorrows(studentId) >= MAX_ACTIVE_BORROWS) {
                return ResponseCode.BORROW_LIMIT_EXCEEDED;
            }
            if (borrowRecordDao.hasActiveBorrow(studentId, isbn)) {
                return ResponseCode.ALREADY_BORROWED;
            }

            // 先原子扣减余量，避免并发下超借
            if (!bookDao.decreaseCurrentNum(isbn)) {
                return ResponseCode.BOOK_NO_STOCK;
            }

            String today = today();
            BorrowRecordVO record = new BorrowRecordVO();
            record.setStudentId(studentId);
            record.setIsbn(isbn);
            record.setBorrowDate(today);
            record.setDueDate(addDays(today, BORROW_DAYS));
            borrowRecordDao.insertBorrow(record);
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 自助还书：仅对本人未归还记录执行，成功后回补余量。
     */
    @Override
    public ResponseCode returnBook(String studentId, String isbn) {
        try {
            if (!borrowRecordDao.markReturned(studentId, isbn, today())) {
                return ResponseCode.NOT_BORROWED;
            }
            bookDao.increaseCurrentNum(isbn);
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 查询某学生的借阅记录。
     */
    @Override
    public List<BorrowRecordVO> listByStudent(String studentId) {
        try {
            return borrowRecordDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询借阅记录失败", e);
        }
    }

    /**
     * 获取当前日期字符串。
     */
    private String today() {
        return new SimpleDateFormat(DATE_PATTERN).format(new Date());
    }

    /**
     * 在给定日期字符串上增加指定天数。
     */
    private String addDays(String dateStr, int days) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(sdf.parse(dateStr));
            calendar.add(Calendar.DAY_OF_MONTH, days);
            return sdf.format(calendar.getTime());
        } catch (ParseException e) {
            return dateStr;
        }
    }
}
