package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 超市购物车条目值对象。
 *
 * <p>包含购物车行本身（id / studentId / goodsId / count / addTime）以及
 * 联表冗余的商品快照信息（goodsName / price / stock / status），便于直接展示。</p>
 *
 * @author vCampus Team
 */
public class CartVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车记录自增 ID */
    private Integer id;
    /** 购物人一卡通号 */
    private String studentId;
    /** 商品编码 */
    private String goodsId;
    /** 商品名称（联表冗余） */
    private String goodsName;
    /** 商品单价（联表冗余） */
    private BigDecimal price;
    /** 商品库存（联表冗余） */
    private int stock;
    /** 商品状态 ON_SHELF/OFF_SHELF（联表冗余） */
    private String status;
    /** 数量 */
    private int count;
    /** 加入时间 (yyyy-MM-dd HH:mm:ss) */
    private String addTime;

    public CartVO() {
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }
}