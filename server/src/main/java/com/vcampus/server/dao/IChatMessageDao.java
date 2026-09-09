package com.vcampus.server.dao;

import com.vcampus.common.vo.ChatMessageVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 二手商品买卖双方聊天记录数据访问接口。
 *
 * @author vCampus Team
 */
public interface IChatMessageDao {

    /**
     * 插入一条聊天消息。
     */
    boolean insert(ChatMessageVO msg) throws SQLException;

    /**
     * 查询某商品下两个用户之间的全部消息（按时间正序）。
     */
    List<ChatMessageVO> listBetween(int itemId, String uidA, String uidB) throws SQLException;

    /**
     * 查询某商品下的全部消息（按时间正序），供会话摘要去重使用。
     */
    List<ChatMessageVO> listByItem(int itemId) throws SQLException;
}
