package com.vcampus.common.message;

/**
 * 服务端统一状态响应码。
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
    /** 该课程成绩已录入 */
    GRADE_ALREADY_EXISTS
}
