package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 超市消费订单值对象。
 *
 * @author vCampus Team
 */
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单流水号 */
    private String orderId;
    /** 购买人学号 / 一卡通号 */
    private String studentId;
    /** 商品编码 */
    private String goodsId;
    /** 商品名称快照 */
    private String goodsName;
    /** 购买数量 */
    private int count;
    /** 交易总金额 */
    private BigDecimal totalPrice;
    /** 下单时间 (yyyy-MM-dd HH:mm:ss) */
    private String orderTime;

    public OrderVO() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(String orderTime) {
        this.orderTime = orderTime;
    }
}
