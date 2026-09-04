package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.OrderVO;

import java.util.List;

/**
 * 超市消费订单业务接口。
 *
 * @author vCampus Team
 */
public interface IOrderService {

    /**
     * 下单结账：库存与余额在同一事务内原子扣减，返回具体业务状态码。
     */
    ResponseCode createOrder(OrderVO order);

    /**
     * 购物车批量结算：在单一事务内校验并扣减购物车内全部商品的库存与
     * 用户余额，逐件生成订单并清空购物车，任一环节失败整体回滚。
     *
     * @param studentId     购物人一卡通号
     * @param createdOrders 成功后回填本次生成的订单列表（可为空，忽略即可）
     * @return 具体业务状态码
     */
    ResponseCode checkoutCart(String studentId, List<OrderVO> createdOrders);

    /**
     * 查询某学生的全部消费订单。
     */
    List<OrderVO> listOrders(String studentId);
}