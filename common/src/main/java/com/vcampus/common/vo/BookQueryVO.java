package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 图书馆藏查询条件。
 */
public class BookQueryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String keyword;
    private String category;
    private int page;
    private int pageSize;

    public BookQueryVO() {
    }

    public BookQueryVO(String keyword, String category, int page, int pageSize) {
        this.keyword = keyword;
        this.category = category;
        this.page = page;
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
