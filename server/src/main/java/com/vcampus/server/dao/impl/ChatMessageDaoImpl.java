package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.ChatMessageVO;
import com.vcampus.server.dao.IChatMessageDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 二手商品聊天记录 JDBC 实现。
 *
 * @author vCampus Team
 */
public class ChatMessageDaoImpl implements IChatMessageDao {

    private static final String COLUMNS = "id, item_id, from_uid, from_name, to_uid, content, send_time";

    @Override
    public boolean insert(ChatMessageVO msg) throws SQLException {
        String sql = "INSERT INTO tbl_chat_message(item_id, from_uid, from_name, to_uid, content, send_time) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, msg.getItemId());
            ps.setString(2, msg.getFromUid());
            ps.setString(3, msg.getFromName());
            ps.setString(4, msg.getToUid());
            ps.setString(5, msg.getContent());
            ps.setString(6, msg.getSendTime());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<ChatMessageVO> listBetween(int itemId, String uidA, String uidB) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_chat_message "
                + "WHERE item_id = ? AND ((from_uid = ? AND to_uid = ?) OR (from_uid = ? AND to_uid = ?)) "
                + "ORDER BY id ASC";
        List<ChatMessageVO> result = new ArrayList<ChatMessageVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setString(2, uidA);
            ps.setString(3, uidB);
            ps.setString(4, uidB);
            ps.setString(5, uidA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    @Override
    public List<ChatMessageVO> listByItem(int itemId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_chat_message "
                + "WHERE item_id = ? ORDER BY id ASC";
        List<ChatMessageVO> result = new ArrayList<ChatMessageVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private ChatMessageVO mapRow(ResultSet rs) throws SQLException {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(rs.getInt("id"));
        vo.setItemId(rs.getInt("item_id"));
        vo.setFromUid(rs.getString("from_uid"));
        vo.setFromName(rs.getString("from_name"));
        vo.setToUid(rs.getString("to_uid"));
        vo.setContent(rs.getString("content"));
        vo.setSendTime(rs.getString("send_time"));
        return vo;
    }
}
