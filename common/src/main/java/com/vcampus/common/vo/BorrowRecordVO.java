package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 图书借阅记录值对象。
 *
 * @author GGbongy
 */
public class BorrowRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录自增 ID */
    private Integer id;
    /** 借阅人学号 / 工号 */
    private String studentId;
    /** 图书 ISBN */
    private String isbn;
    /** 图书名称（联表冗余，便于展示） */
    private String title;
    /** 作者（联表冗余，便于展示） */
    private String author;
    /** 借出日期 (YYYY-MM-DD) */
    private String borrowDate;
    /** 应还日期 (YYYY-MM-DD) */
    private String dueDate;
    /** 归还日期，未归还时为空 */
    private String returnDate;
    /** 状态: BORROWED / RETURNED */
    private String status;
    /** 续借次数 */
    private int renewCount;
    /** 逾期天数（0 表示未逾期） */
    private int overdueDays;

    public BorrowRecordVO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(String borrowDate) {
        this.borrowDate = borrowDate;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRenewCount() {
        return renewCount;
    }

    public void setRenewCount(int renewCount) {
        this.renewCount = renewCount;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(int overdueDays) {
        this.overdueDays = overdueDays;
    }
}
