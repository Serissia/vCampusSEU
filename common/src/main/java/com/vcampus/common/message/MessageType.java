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

    /** 教务课程管理 */
    COURSE_QUERY,
    COURSE_ADD,
    COURSE_UPDATE,
    COURSE_DISABLE,

    /** 教务选课流转 */
    COURSE_SELECT,
    COURSE_DROP,
    COURSE_TIMETABLE,

    /** 成绩管理 */
    GRADE_SUBMIT,
    GRADE_QUERY,
    GRADE_STATISTICS,

    /** 图书馆图书检索与采编 */
    BOOK_QUERY,
    BOOK_ADD,
    BOOK_UPDATE,
    BOOK_DELETE,

    /** 图书借还流转 */
    BOOK_BORROW,
    BOOK_RETURN,

    /** 借阅记录查询 */
    BORROW_MY_LIST,
    BORROW_BY_STUDENT,

    /** 图书电子资源上传、下载与删除 */
    BOOK_RESOURCE_UPLOAD,
    BOOK_RESOURCE_DOWNLOAD,
    BOOK_RESOURCE_DELETE
}
