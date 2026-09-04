package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.CartVO;
import com.vcampus.server.dao.ICartDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 超市购物车 JDBC 实现。
 *
 * @author vCampus Team
 */
public class CartDaoImpl implements ICartDao {

    /** 联表查询字段：购物车行 + 商品快照 */
    private static final String JOIN_COLUMNS =
            "c.id, c.student_id, c.goods_id, c.count, c.add_time, "
                    + "g.goods_name, g.price, g.stock, g.status";

    /**
     * 加入购物车：同商品存在则累加数量，否则插入新行。
     */
    @Override
    public boolean addOrIncrease(String studentId, String goodsId, int count, String addTime) throws SQLException {
        String sql = "INSERT INTO tbl_cart(student_id, goods_id, count, add_time) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE count = count + ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, goodsId);
            ps.setInt(3, count);
            ps.setString(4, addTime);
            ps.setInt(5, count);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新某条目的数量。
     */
    @Override
    public boolean updateCount(String studentId, String goodsId, int count) throws SQLException {
        String sql = "UPDATE tbl_cart SET count = ? WHERE student_id = ? AND goods_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setString(2, studentId);
            ps.setString(3, goodsId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 移除某条目。
     */
    @Override
    public boolean removeItem(String studentId, String goodsId) throws SQLException {
        String sql = "DELETE FROM tbl_cart WHERE student_id = ? AND goods_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, goodsId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 清空某用户的购物车。
     */
    @Override
    public boolean clearByStudent(String studentId) throws SQLException {
        String sql = "DELETE FROM tbl_cart WHERE student_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 在同一事务内清空购物车（结算成功后调用）。
     */
    @Override
    public boolean clearByStudent(Connection conn, String studentId) throws SQLException {
        String sql = "DELETE FROM tbl_cart WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 查询某用户的购物车（联表商品信息）。
     */
    @Override
    public List<CartVO> listByStudent(String studentId) throws SQLException {
        String sql = "SELECT " + JOIN_COLUMNS + " FROM tbl_cart c "
                + "JOIN tbl_goods g ON c.goods_id = g.goods_id "
                + "WHERE c.student_id = ? ORDER BY c.id";
        List<CartVO> result = new ArrayList<CartVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapCart(rs));
                }
            }
        }
        return result;
    }

    /**
     * 在同一事务内查询并锁定购物车及其商品行（结算使用）。
     */
    @Override
    public List<CartVO> listByStudentForUpdate(Connection conn, String studentId) throws SQLException {
        String sql = "SELECT " + JOIN_COLUMNS + " FROM tbl_cart c "
                + "JOIN tbl_goods g ON c.goods_id = g.goods_id "
                + "WHERE c.student_id = ? ORDER BY c.id FOR UPDATE";
        List<CartVO> result = new ArrayList<CartVO>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapCart(rs));
                }
            }
        }
        return result;
    }

    /**
     * 将 ResultSet 当前行转换为 CartVO。
     */
    private CartVO mapCart(ResultSet rs) throws SQLException {
        CartVO cart = new CartVO();
        cart.setId(rs.getInt("id"));
        cart.setStudentId(rs.getString("student_id"));
        cart.setGoodsId(rs.getString("goods_id"));
        cart.setGoodsName(rs.getString("goods_name"));
        cart.setPrice(rs.getBigDecimal("price"));
        cart.setStock(rs.getInt("stock"));
        cart.setStatus(rs.getString("status"));
        cart.setCount(rs.getInt("count"));
        cart.setAddTime(rs.getString("add_time"));
        return cart;
    }
}