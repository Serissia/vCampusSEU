package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.GoodsVO;
import com.vcampus.server.dao.IGoodsDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 超市商品 JDBC 实现。
 *
 * @author vCampus Team
 */
public class GoodsDaoImpl implements IGoodsDao {

    private static final String COLUMNS = "goods_id, goods_name, price, stock, description, status";

    /**
     * 按商品编码或名称进行模糊查询，关键字为空时返回全部商品（含已下架）。
     */
    @Override
    public List<GoodsVO> queryGoods(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_goods WHERE goods_id LIKE ? OR goods_name LIKE ?";
        List<GoodsVO> result = new ArrayList<GoodsVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + keyword + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGoods(rs));
                }
            }
        }
        return result;
    }

    /**
     * 按商品编码精确查询商品。
     */
    @Override
    public GoodsVO findById(String goodsId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_goods WHERE goods_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goodsId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapGoods(rs) : null;
            }
        }
    }

    /**
     * 在同一事务内按商品编码精确查询并锁定商品行。
     */
    @Override
    public GoodsVO findByIdForUpdate(Connection conn, String goodsId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_goods WHERE goods_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goodsId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapGoods(rs) : null;
            }
        }
    }

    /**
     * 在同一事务内原子扣减商品库存，库存不足时拒绝（返回 false）。
     */
    @Override
    public boolean decreaseStock(Connection conn, String goodsId, int count) throws SQLException {
        String sql = "UPDATE tbl_goods SET stock = stock - ? WHERE goods_id = ? AND stock >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setString(2, goodsId);
            ps.setInt(3, count);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 新增商品，未指定状态时默认上架。
     */
    @Override
    public boolean insertGoods(GoodsVO goods) throws SQLException {
        String sql = "INSERT INTO tbl_goods(goods_id, goods_name, price, stock, description, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goods.getGoodsId());
            ps.setString(2, goods.getGoodsName());
            ps.setBigDecimal(3, goods.getPrice());
            ps.setInt(4, goods.getStock());
            ps.setString(5, goods.getDescription());
            ps.setString(6, goods.getStatus() == null ? "ON_SHELF" : goods.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新商品信息（商品编码作为业务主键不可变更，状态独立管理）。
     */
    @Override
    public boolean updateGoods(GoodsVO goods) throws SQLException {
        String sql = "UPDATE tbl_goods SET goods_name=?, price=?, stock=?, description=? "
                + "WHERE goods_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goods.getGoodsName());
            ps.setBigDecimal(2, goods.getPrice());
            ps.setInt(3, goods.getStock());
            ps.setString(4, goods.getDescription());
            ps.setString(5, goods.getGoodsId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 删除商品。
     */
    @Override
    public boolean deleteGoods(String goodsId) throws SQLException {
        String sql = "DELETE FROM tbl_goods WHERE goods_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goodsId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新商品上下架状态。
     */
    @Override
    public boolean updateStatus(String goodsId, String status) throws SQLException {
        String sql = "UPDATE tbl_goods SET status = ? WHERE goods_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, goodsId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将 ResultSet 当前行转换为 GoodsVO。
     */
    private GoodsVO mapGoods(ResultSet rs) throws SQLException {
        GoodsVO goods = new GoodsVO();
        goods.setGoodsId(rs.getString("goods_id"));
        goods.setGoodsName(rs.getString("goods_name"));
        goods.setPrice(rs.getBigDecimal("price"));
        goods.setStock(rs.getInt("stock"));
        goods.setDescription(rs.getString("description"));
        goods.setStatus(rs.getString("status"));
        return goods;
    }
}