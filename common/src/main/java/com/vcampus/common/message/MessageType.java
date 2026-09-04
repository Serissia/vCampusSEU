package com.vcampus.common.message;

import java.io.Serializable;

/**
 * 统一通信动作枚举
 *
 * @author GGbongy
 * @version 1.0
 */
public enum MessageType implements Serializable {

    /** 统一登录与会话 */
    LOGIN,
    LOGOUT,
    HEARTBEAT,

    /** 个人信息与档案管理 */
    GET_USER_INFO,
    UPDATE_USER_INFO,
    CHANGE_PASSWORD,

    /** 用户管理（系统管理员） */
    USER_REGISTER,
    USER_LIST,
    USER_UPDATE,
    USER_DELETE,
    USER_RESET_PASSWORD,

    /** 教务课程管理 */
    COURSE_QUERY,
    COURSE_ADD,
    COURSE_UPDATE,
    COURSE_DISABLE,
    COURSE_DELETE,
    COURSE_APPROVE,
    COURSE_REJECT,
    COURSE_LIST_ALL,
    COURSE_QUERY_BY_TEACHER,
    COURSE_QUERY_BY_SEMESTER,
    COURSE_PENDING_LIST,
    COURSE_SCHEDULE,
    COURSE_WEEK_SCHEDULE,

    /** 教务选课流转 */
    COURSE_SELECT,
    COURSE_DROP,
    COURSE_TIMETABLE,

    /** 成绩管理 */
    GRADE_SUBMIT,
    GRADE_QUERY,
    GRADE_QUERY_BY_COURSE,
    GRADE_STATISTICS,

    /** 图书馆图书检索与采编 */
    BOOK_QUERY,
    BOOK_ADD,
    BOOK_UPDATE,
    BOOK_DELETE,

    /** 图书借还流转 */
    BOOK_BORROW,
    BOOK_RETURN,
    BOOK_RENEW,

    /** 借阅记录查询 */
    BORROW_MY_LIST,
    BORROW_BY_STUDENT,

    /** 图书电子资源上传、下载与删除 */
    BOOK_RESOURCE_UPLOAD,
    BOOK_RESOURCE_DOWNLOAD,
    BOOK_RESOURCE_DELETE,

    /** 校园超市商品检索 */
    GOODS_QUERY,

    /** 校园超市商品管理 */
    GOODS_ADD,
    GOODS_UPDATE,
    GOODS_DELETE,

    /** 商品强制下架（仅管理员） */
    GOODS_OFF_SHELF,

    /** 校园超市订单创建 */
    ORDER_CREATE,

    /** 校园超市订单查询 */
    ORDER_QUERY,

    /** 一卡通在线充值 */
    PAYMENT_RECHARGE,

    /** 一卡通余额查询 */
    PAYMENT_BALANCE
}
