package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CartVO;

import java.util.List;

/**
 * 超市购物车业务接口。
 *
 * @author vCampus Team
 */
public interface ICartService {

    /**
     * 加入购物车（同商品累加数量），返回具体业务状态码。
     */
    ResponseCode addItem(String studentId, String goodsId, int count);

    /**
     * 查询某用户购物车（含商品快照）。
     */
    List<CartVO> listCart(String studentId);

    /**
     * 更新购物车条目数量。
     */
    ResponseCode updateCount(String studentId, String goodsId, int count);

    /**
     * 移除购物车条目。
     */
    ResponseCode removeItem(String studentId, String goodsId);

    /**
     * 清空购物车。
     */
    ResponseCode clearCart(String studentId);
}