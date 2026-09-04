package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 超市订单统计值对象。
 *
 * @author vCampus Team
 */
public class StatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总订单数 */
    private long totalOrders;
    /** 总销售额 */
    private BigDecimal totalRevenue;
    /** 热门商品 Top3（按销量排序） */
    private List<TopProduct> topProducts;

    public StatisticsVO() {
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public List<TopProduct> getTopProducts() {
        return topProducts;
    }

    public void setTopProducts(List<TopProduct> topProducts) {
        this.topProducts = topProducts;
    }

    /**
     * 热门商品条目（含销量与销售额）。
     */
    public static class TopProduct implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商品名称 */
        private String goodsName;
        /** 累计销量 */
        private long totalCount;
        /** 累计销售额 */
        private BigDecimal revenue;

        public TopProduct() {
        }

        public String getGoodsName() {
            return goodsName;
        }

        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }
    }
}