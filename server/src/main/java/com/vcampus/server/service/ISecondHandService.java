package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.SecondHandVO;
import java.math.BigDecimal;

import java.util.List;

/**
 * 校园二手市场业务接口。
 *
 * @author vCampus Team
 */
public interface ISecondHandService {

    /**
     * 查询全部在售商品。
     */
    List<SecondHandVO> listOnSale();

    /**
     * 查询全部待审核商品（管理员）。
     */
    List<SecondHandVO> listPending();

    /**
     * 查询某卖家发布的全部商品（含各审核/交易状态）。
     */
    List<SecondHandVO> listMine(String uid);

    /**
     * 学生发布二手商品（状态进入待审核）。
     */
    ResponseCode publish(String uid, SecondHandVO vo);

    /**
     * 管理员审核二手商品：通过则上架，拒绝则标记 REJECTED。
     */
    ResponseCode review(String uid, Integer id, boolean approve);

    /**
     * 卖家下架自己发布的商品。
     */
    ResponseCode offShelf(String uid, Integer id);

    /**
     * 购买二手商品：买家扣款、卖家收款、商品标记已售、写入交易订单，全程单事务。
     */
    ResponseCode buy(String uid, Integer id);

    /**
     * 卖家修改自己商品的定价（仅在售且本人可改），写入调价日志。
     */
    ResponseCode updatePrice(String uid, Integer id, BigDecimal newPrice);
}