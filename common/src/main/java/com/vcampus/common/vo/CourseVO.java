package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 课程信息值对象。
 *
 * @author xingyi852
 */
public class CourseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 课程代码 */
    private String courseCode;
    /** 课程名称 */
    private String courseName;
    /** 学分 */
    private double credit;
    /** 任课教师工号 */
    private String teacherId;
    /** 任课教师姓名 */
    private String teacherName;
    /** 课程容量 */
    private int capacity;
    /** 已选人数 */
    private int selectedCount;
    /** 开课学期 */
    private String semester;
    /** 上课时间 */
    private String classTime;
    /** 上课教室 */
    private String location;
    /**
     * 课程状态。
     *
     * <p>该字段描述课程当前所处的业务生命周期，服务端在选课、课程目录展示
     * 与停开课操作时都会读取它。当前约定取值如下：</p>
     *
     * <ul>
     *   <li>{@code ACTIVE}：正常开课，允许学生查询和选课；</li>
     *   <li>{@code DISABLED}：已停开，不允许再选课，但历史数据仍然保留；</li>
     *   <li>{@code PENDING}：待审核或待发布状态，可用于后续扩展排课审批流程。</li>
     * </ul>
     *
     * <p>典型转换流程：</p>
     *
     * <pre>
     * PENDING --审核通过--> ACTIVE --停开--> DISABLED
     * </pre>
     *
     * <p>注意：当前业务代码中已经用 {@code "ACTIVE"} 和 {@code "DISABLED"}
     * 字符串进行比较，因此修改状态值时需要同时检查
     * {@code CourseSelectionServiceImpl} 与相关 SQL 逻辑。</p>
     */
    private String status;

    public CourseVO() {
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public double getCredit() {
        return credit;
    }

    public void setCredit(double credit) {
        this.credit = credit;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(int selectedCount) {
        this.selectedCount = selectedCount;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getClassTime() {
        return classTime;
    }

    public void setClassTime(String classTime) {
        this.classTime = classTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
