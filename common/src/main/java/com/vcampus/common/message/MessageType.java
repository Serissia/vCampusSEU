package com.vcampus.common.message;

/**
 * 前后端共享的业务动作枚举。
 */
public enum MessageType {
    /** 统一登录 */
    LOGIN,

    /** 课程目录管理 */
    COURSE_ADD,
    COURSE_UPDATE,
    COURSE_DISABLE,
    COURSE_QUERY,

    /** 学生选退课 */
    COURSE_SELECT,
    COURSE_DROP,
    COURSE_TIMETABLE,

    /** 成绩管理 */
    GRADE_SUBMIT,
    GRADE_QUERY,
    GRADE_STATISTICS
}
