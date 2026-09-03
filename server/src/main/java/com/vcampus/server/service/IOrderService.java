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
     * 查询某学生的全部消费订单。
     */
    List<OrderVO> listOrders(String studentId);
}
