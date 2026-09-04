package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.CartVO;
import com.vcampus.common.vo.GoodsVO;
import com.vcampus.server.dao.ICartDao;
import com.vcampus.server.dao.IGoodsDao;
import com.vcampus.server.dao.impl.CartDaoImpl;
import com.vcampus.server.dao.impl.GoodsDaoImpl;
import com.vcampus.server.service.ICartService;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * 超市购物车业务实现。
 *
 * @author vCampus Team
 */
public class CartServiceImpl implements ICartService {

    /** 购物车单个商品数量上限 */
    private static final int MAX_PER_ITEM = 99;

    private final ICartDao cartDao = new CartDaoImpl();
    private final IGoodsDao goodsDao = new GoodsDaoImpl();

    /**
     * 加入购物车：仅允许添加在售商品，同商品数量累加。
     */
    @Override
    public ResponseCode addItem(String studentId, String goodsId, int count) {
        try {
            if (studentId == null || goodsId == null || goodsId.trim().isEmpty() || count <= 0) {
                return ResponseCode.INVALID_REQUEST;
            }
            // 校验商品存在且在售
            GoodsVO goods = goodsDao.findById(goodsId.trim());
            if (goods == null || goods.getStatus() == null || !"ON_SHELF".equals(goods.getStatus())) {
                return ResponseCode.GOODS_NOT_FOUND;
            }
            boolean ok = cartDao.addOrIncrease(studentId.trim(), goodsId.trim(),
                    Math.min(count, MAX_PER_ITEM), DateUtil.format(new Date()));
            return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    /**
     * 查询某用户购物车（含商品快照）。
     */
    @Override
    public List<CartVO> listCart(String studentId) {
        try {
            return cartDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询购物车失败", e);
        }
    }

    /**
     * 更新购物车条目数量。
     */
    @Override
    public ResponseCode updateCount(String studentId, String goodsId, int count) {
        try {
            if (studentId == null || goodsId == null || goodsId.trim().isEmpty() || count <= 0) {
                return ResponseCode.INVALID_REQUEST;
            }
            boolean ok = cartDao.updateCount(studentId.trim(), goodsId.trim(),
                    Math.min(count, MAX_PER_ITEM));
            return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    /**
     * 移除购物车条目。
     */
    @Override
    public ResponseCode removeItem(String studentId, String goodsId) {
        try {
            if (studentId == null || goodsId == null || goodsId.trim().isEmpty()) {
                return ResponseCode.INVALID_REQUEST;
            }
            cartDao.removeItem(studentId.trim(), goodsId.trim());
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    /**
     * 清空购物车。
     */
    @Override
    public ResponseCode clearCart(String studentId) {
        try {
            if (studentId == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            cartDao.clearByStudent(studentId.trim());
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }
}