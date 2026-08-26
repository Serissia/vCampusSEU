package com.vcampus.common.message;

import java.io.Serializable;

/**
 * 统一通信动作枚举
 *
 * @author vCampus Team
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
    GRADE_STATISTICS
}
