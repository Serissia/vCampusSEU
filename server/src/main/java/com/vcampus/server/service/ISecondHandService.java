package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.SecondHandVO;

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
     * 学生发布二手商品。
     */
    ResponseCode publish(String uid, SecondHandVO vo);

    /**
     * 卖家下架自己发布的商品。
     */
    ResponseCode offShelf(String uid, Integer id);

    /**
     * 购买二手商品：买家扣款、卖家收款、商品标记已售，全程单事务。
     */
    ResponseCode buy(String uid, Integer id);
}