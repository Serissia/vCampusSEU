package com.vcampus.common.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 选课记录值对象。
 */
public class CourseSelectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学生账号 */
    private String studentId;
    /** 课程代码 */
    private String courseCode;
    /** 选课时间 */
    private Date selectTime;
    /** 选课状态 */
    private String status;

    public CourseSelectionVO() {
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public Date getSelectTime() {
        return selectTime;
    }

    public void setSelectTime(Date selectTime) {
        this.selectTime = selectTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
