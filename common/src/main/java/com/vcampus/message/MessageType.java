package com.vcampus.message;

import java.io.Serializable;

/**
 * 业务请求/响应动作类型枚举
 * @author Serissia
 */
public enum MessageType implements Serializable {
    /** 基础鉴权与心跳 */
    // 登录验证
    MSG_LOGIN,
    // 注销下线
    MSG_LOGOUT,
    // 长连接心跳
    MSG_HEARTBEAT,

    /** 个人信息管理 */
    // 查询用户/学籍信息
    MSG_GET_USER_INFO,
    // 更新用户个人信息
    MSG_UPDATE_USER_INFO,
    // 修改密码
    MSG_CHANGE_PASSWORD,

    /** 教务管理 */
    // 获取课程列表
    MSG_GET_COURSE_LIST,
    // 学生选课
    MSG_SELECT_COURSE,
    // 学生退课
    MSG_DROP_COURSE,
    // 查看个人课表与成绩
    MSG_GET_MY_SCHEDULE,

    /** 虚拟图书馆 */
    // 查询图书列表
    MSG_GET_BOOK_LIST,
    // 借阅图书
    MSG_BORROW_BOOK,
    // 归还图书
    MSG_RETURN_BOOK,
    // 获取借阅记录
    MSG_GET_MY_BORROWS,

    /** 虚拟商店 */
    // 查询商品列表
    MSG_GET_GOODS_LIST,
    // 购买商品/购物车结算
    MSG_PURCHASE_GOODS,
    // 查询个人订单流水
    MSG_GET_MY_ORDERS
}