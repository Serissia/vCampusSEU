package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.ChatMessageVO;

import java.util.List;

/**
 * 二手商品买卖双方聊天业务接口。
 *
 * @author vCampus Team
 */
public interface IChatService {

    /**
     * 发送一条聊天消息。
     *
     * @param fromUid 发送者一卡通号
     * @param itemId  关联的二手商品 ID
     * @param toUid   接收者一卡通号
     * @param content 消息内容
     */
    ResponseCode send(String fromUid, Integer itemId, String toUid, String content);

    /**
     * 查询某商品下两个用户之间的历史消息（按时间正序）。
     */
    List<ChatMessageVO> history(Integer itemId, String uidA, String uidB);

    /**
     * 查询某商品下与指定卖家相关的会话摘要：按买家去重，返回每个买家的最后一条消息。
     */
    List<ChatMessageVO> conversations(Integer itemId, String sellerUid);
}
