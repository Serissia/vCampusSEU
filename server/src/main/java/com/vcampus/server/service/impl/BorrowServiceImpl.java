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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
     * 学生自助续借：仅限借阅中、未逾期、未续借过的记录，续借后应还日期顺延。
     */
    @Override
    public ResponseCode renew(String studentId, String isbn) {
        try {
            BorrowRecordVO record = borrowRecordDao.findActiveBorrow(studentId, isbn);
            if (record == null) {
                return ResponseCode.NOT_BORROWED;
            }
            if (record.getRenewCount() >= 1) {
                return ResponseCode.RENEW_LIMIT_EXCEEDED;
            }
            LocalDate today = LocalDate.now();
            if (isOverdue(record, today)) {
                return ResponseCode.OVERDUE;
            }
            String newDueDate = addDays(record.getDueDate(), BORROW_DAYS);
            borrowRecordDao.renew(studentId, isbn, newDueDate);
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
            List<BorrowRecordVO> records = borrowRecordDao.listByStudent(studentId);
            LocalDate today = LocalDate.now();
            for (BorrowRecordVO record : records) {
                record.setOverdueDays(computeOverdueDays(record, today));
            }
            return records;
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

    /**
     * 判断借阅记录是否已逾期（未归还且应还日期早于今天）。
     */
    private boolean isOverdue(BorrowRecordVO record, LocalDate today) {
        if (record.getDueDate() == null || record.getDueDate().trim().isEmpty()) {
            return false;
        }
        try {
            LocalDate due = LocalDate.parse(record.getDueDate().trim());
            return due.isBefore(today);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算借阅记录逾期天数（未归还且已逾期时返回正整数，否则为 0）。
     */
    private int computeOverdueDays(BorrowRecordVO record, LocalDate today) {
        if (!"BORROWED".equals(record.getStatus())) {
            return 0;
        }
        if (record.getDueDate() == null || record.getDueDate().trim().isEmpty()) {
            return 0;
        }
        try {
            LocalDate due = LocalDate.parse(record.getDueDate().trim());
            long days = ChronoUnit.DAYS.between(due, today);
            return days > 0 ? (int) days : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
