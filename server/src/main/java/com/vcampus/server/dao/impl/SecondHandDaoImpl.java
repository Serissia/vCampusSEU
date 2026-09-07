package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.server.dao.ISecondHandDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 校园二手市场 JDBC 实现。
 *
 * @author vCampus Team
 */
public class SecondHandDaoImpl implements ISecondHandDao {

    private static final String COLUMNS =
            "id, seller_id, seller_name, title, description, price, status, created_time";

    @Override
    public List<SecondHandVO> listOnSale() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_second_hand "
                + "WHERE status = 'ON_SALE' ORDER BY id DESC";
        List<SecondHandVO> result = new ArrayList<SecondHandVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    @Override
    public List<SecondHandVO> listPending() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_second_hand "
                + "WHERE status = 'PENDING' ORDER BY id DESC";
        List<SecondHandVO> result = new ArrayList<SecondHandVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    @Override
    public List<SecondHandVO> listBySeller(String sellerId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_second_hand "
                + "WHERE seller_id = ? ORDER BY id DESC";
        List<SecondHandVO> result = new ArrayList<SecondHandVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    @Override
    public SecondHandVO findByIdForUpdate(Connection conn, int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_second_hand WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    @Override
    public boolean insert(SecondHandVO vo) throws SQLException {
        String sql = "INSERT INTO tbl_second_hand(seller_id, seller_name, title, description, price, status, created_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vo.getSellerId());
            ps.setString(2, vo.getSellerName());
            ps.setString(3, vo.getTitle());
            ps.setString(4, vo.getDescription());
            ps.setBigDecimal(5, vo.getPrice());
            ps.setString(6, vo.getStatus() == null ? "PENDING" : vo.getStatus());
            ps.setString(7, vo.getCreatedTime());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean markSoldById(Connection conn, int id) throws SQLException {
        String sql = "UPDATE tbl_second_hand SET status = 'SOLD' WHERE id = ? AND status = 'ON_SALE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean review(int id, String targetStatus) throws SQLException {
        String sql = "UPDATE tbl_second_hand SET status = ? WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetStatus);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean offShelf(String sellerId, int id) throws SQLException {
        String sql = "UPDATE tbl_second_hand SET status = 'SOLD' "
                + "WHERE id = ? AND seller_id = ? AND status = 'ON_SALE'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, sellerId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean insertOrder(Connection conn, OrderVO order) throws SQLException {
        String sql = "INSERT INTO tbl_second_hand_order(order_id, buyer_id, seller_id, item_id, title, price, order_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, order.getOrderId());
            ps.setString(2, order.getStudentId());
            ps.setString(3, order.getSellerId());
            ps.setInt(4, Integer.parseInt(order.getGoodsId()));
            ps.setString(5, order.getGoodsName());
            ps.setBigDecimal(6, order.getTotalPrice());
            ps.setString(7, order.getOrderTime());
            return ps.executeUpdate() > 0;
        }
    }

    private SecondHandVO mapRow(ResultSet rs) throws SQLException {
        SecondHandVO vo = new SecondHandVO();
        vo.setId(rs.getInt("id"));
        vo.setSellerId(rs.getString("seller_id"));
        vo.setSellerName(rs.getString("seller_name"));
        vo.setTitle(rs.getString("title"));
        vo.setDescription(rs.getString("description"));
        vo.setPrice(rs.getBigDecimal("price"));
        vo.setStatus(rs.getString("status"));
        vo.setCreatedTime(rs.getString("created_time"));
        return vo;
    }
}