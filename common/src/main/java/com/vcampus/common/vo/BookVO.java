package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 图书信息值对象。
 *
 * @author GGbongy
 */
public class BookVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ISBN 编号 */
    private String isbn;
    /** 图书名称 */
    private String title;
    /** 作者 */
    private String author;
    /** 出版社 */
    private String publisher;
    /** 存放位置 / 书架 */
    private String location;
    /** 电子资源文件名（服务器端存储索引，为空表示未录入） */
    private String resourceFile;
    /** 馆藏总数 */
    private int totalNum;
    /** 当前可借余量 */
    private int currentNum;

    public BookVO() {
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

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(String resourceFile) {
        this.resourceFile = resourceFile;
    }

    public int getTotalNum() {
        return totalNum;
    }

    public void setTotalNum(int totalNum) {
        this.totalNum = totalNum;
    }

    public int getCurrentNum() {
        return currentNum;
    }

    public void setCurrentNum(int currentNum) {
        this.currentNum = currentNum;
    }
}
