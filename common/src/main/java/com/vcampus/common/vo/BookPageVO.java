package com.vcampus.common.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图书馆藏分页结果。
 */
public class BookPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<BookVO> items = new ArrayList<>();
    private int page;
    private int pageSize;
    private int total;
    private boolean hasMore;

    public List<BookVO> getItems() {
        return items;
    }

    public void setItems(List<BookVO> items) {
        this.items = items == null ? new ArrayList<>() : items;
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

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
