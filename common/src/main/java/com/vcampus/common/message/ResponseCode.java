package com.vcampus.common.message;

/**
 * 服务端统一状态响应码。
 *
 * @author GGbongy
 */
public enum ResponseCode {
    /** 操作成功 */
    SUCCESS,
    /** 通用失败 */
    FAIL,
    /** 未登录或身份校验失败 */
    UNAUTHORIZED,
    /** 请求参数非法 */
    INVALID_REQUEST,
    /** 课程不存在或已停开 */
    COURSE_NOT_FOUND,
    /** 课程容量已满 */
    COURSE_FULL,
    /** 已经选择该课程 */
    ALREADY_SELECTED,
    /** 上课时间冲突 */
    COURSE_TIME_CONFLICT,
    /** 超过学分上限 */
    CREDIT_LIMIT_EXCEEDED,
    /** 课程状态不合法 */
    COURSE_STATUS_INVALID,
    /** 该课程成绩已录入 */
    GRADE_ALREADY_EXISTS,
    /** 图书不存在 */
    BOOK_NOT_FOUND,
    /** 图书库存不足 */
    BOOK_NO_STOCK,
    /** 已达借阅数量上限 */
    BORROW_LIMIT_EXCEEDED,
    /** 已借阅该书且尚未归还 */
    ALREADY_BORROWED,
    /** 未借阅该书 */
    NOT_BORROWED,
    /** 当前角色无权执行该操作 */
    PERMISSION_DENIED
}
